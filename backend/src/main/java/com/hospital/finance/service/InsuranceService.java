package com.hospital.finance.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.finance.dto.InsuranceSettleRequest;
import com.hospital.finance.dto.InsuranceSettleVO;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizChargeItem;
import com.hospital.finance.entity.BizInsuranceSettle;
import com.hospital.finance.mapper.BizChargeItemMapper;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizInsuranceSettleMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 医保结算服务（一期 Mock）。
 *
 * <p><b>为什么不直接写死一个固定比例</b>：结算金额必须来自收费明细的
 * 医保类别与报销比例，而不是「总额 × 某个常数」。真实医保是按目录逐项算的，
 * 用常数会让「甲类全报、丙类全自费」这种最基本的规则都体现不出来，
 * 将来换真接口时整套算法要推翻重来。</p>
 *
 * <p>上送 / 返回报文原样落库：真对接之后排查「医保说失败但我们认为成功」
 * 这类扯皮，靠的就是这些原文。只存一个状态等于两眼一抹黑。</p>
 */
@Slf4j
@Service
public class InsuranceService {

    private static final int PENDING = 0;
    private static final int SETTLED = 1;
    private static final int FAILED = 2;

    /** 甲类 / 乙类 / 丙类 的报销比例（丙类全额自费） */
    private static final Map<Integer, BigDecimal> TYPE_RATIO = Map.of(
            1, new BigDecimal("0.90"),
            2, new BigDecimal("0.70"),
            3, BigDecimal.ZERO);

    /** 医保封顶线：超过部分全额自费，避免 Mock 算出「报了 200%」这种荒唐数 */
    private static final BigDecimal CAP = new BigDecimal("5000.00");

    private final BizInsuranceSettleMapper settleMapper;
    private final BizChargeMapper chargeMapper;
    private final BizChargeItemMapper chargeItemMapper;
    private final BizPatientMapper patientMapper;

    public InsuranceService(BizInsuranceSettleMapper settleMapper,
                            BizChargeMapper chargeMapper,
                            BizChargeItemMapper chargeItemMapper,
                            BizPatientMapper patientMapper) {
        this.settleMapper = settleMapper;
        this.chargeMapper = chargeMapper;
        this.chargeItemMapper = chargeItemMapper;
        this.patientMapper = patientMapper;
    }

    /**
     * 提交结算。
     *
     * <p>已经结算成功的单不接受重复结算 —— 重复上送在真接口里
     * 会被判为「重复交易」，轻则退回，重则真的付两遍。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long settle(InsuranceSettleRequest request) {
        BizCharge charge = chargeMapper.selectById(request.getChargeId());
        if (charge == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "收费单不存在");
        }
        if (charge.getPayStatus() == null || charge.getPayStatus() == 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该收费单尚未支付，不能结算");
        }

        BizInsuranceSettle latest = settleMapper.selectLatestByCharge(charge.getId());
        if (latest != null && Integer.valueOf(SETTLED).equals(latest.getSettleStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                    "该收费单已结算成功（结算号 " + latest.getSettleNo() + "），请勿重复上送");
        }

        BigDecimal total = nvl(charge.getActualAmount());
        BigDecimal insuranceAmount = computeInsuranceAmount(charge.getId(), total);
        BigDecimal selfAmount = total.subtract(insuranceAmount);

        String requestBody = mockRequest(charge, request.getInsuranceType(), total);
        // 一期 Mock：一律返回成功。真接口替换时只改这一段，表结构与金额算法不动
        String responseBody = mockResponse(insuranceAmount, selfAmount);

        BizInsuranceSettle entity = new BizInsuranceSettle();
        entity.setSettleNo("YB" + charge.getChargeNo());
        entity.setChargeId(charge.getId());
        entity.setInsuranceType(request.getInsuranceType().trim());
        entity.setTotalAmount(total);
        entity.setInsuranceAmount(insuranceAmount);
        entity.setSelfAmount(selfAmount);
        entity.setSettleStatus(SETTLED);
        entity.setRequestBody(requestBody);
        entity.setResponseBody(responseBody);
        entity.setSettleTime(LocalDateTime.now());
        settleMapper.insert(entity);

        log.info("医保结算成功 settleNo={} chargeId={} 统筹={} 自付={} operator={}",
                entity.getSettleNo(), charge.getId(), insuranceAmount, selfAmount,
                CurrentUser.idOrNull());
        return entity.getId();
    }

    public InsuranceSettleVO detailByCharge(Long chargeId) {
        BizInsuranceSettle entity = settleMapper.selectLatestByCharge(chargeId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "该收费单尚无医保结算记录");
        }
        return toVO(entity);
    }

    /**
     * 逐项按医保类别算统筹支付额。
     *
     * <p>明细里存的是项目快照，类别与比例取自价目表当时的配置；
     * 取不到就按丙类（全自费）处理 —— 宁可少报也不能虚报，
     * 少报可以补结算，虚报是要追责的。</p>
     */
    private BigDecimal computeInsuranceAmount(Long chargeId, BigDecimal total) {
        List<BizChargeItem> items = chargeItemMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BizChargeItem>lambdaQuery()
                        .eq(BizChargeItem::getChargeId, chargeId));
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal payable = BigDecimal.ZERO;
        for (BizChargeItem item : items) {
            Integer type = item.getItemType() == null ? 3 : normalizeType(item.getItemType());
            BigDecimal ratio = TYPE_RATIO.getOrDefault(type, BigDecimal.ZERO);
            payable = payable.add(nvl(item.getAmount()).multiply(ratio));
        }
        return payable.min(CAP).min(total).setScale(2, RoundingMode.HALF_UP);
    }

    /** 收费明细 item_type 是 1药品 2诊疗 3检查 4床位，这里映射到医保甲乙丙类 */
    private Integer normalizeType(Integer itemType) {
        return switch (itemType) {
            case 1 -> 1;
            case 2, 3 -> 2;
            default -> 3;
        };
    }

    private String mockRequest(BizCharge charge, String insuranceType, BigDecimal total) {
        return "{\"chargeNo\":\"" + charge.getChargeNo()
                + "\",\"insuranceType\":\"" + insuranceType
                + "\",\"totalAmount\":" + total + "}";
    }

    private String mockResponse(BigDecimal insuranceAmount, BigDecimal selfAmount) {
        return "{\"resultCode\":\"0\",\"resultMsg\":\"success\""
                + ",\"insuranceAmount\":" + insuranceAmount
                + ",\"selfAmount\":" + selfAmount + "}";
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private InsuranceSettleVO toVO(BizInsuranceSettle entity) {
        BizCharge charge = chargeMapper.selectById(entity.getChargeId());
        String patientName = null;
        if (charge != null && charge.getPatientId() != null) {
            BizPatient patient = patientMapper.selectById(charge.getPatientId());
            patientName = patient == null ? null : patient.getName();
        }
        return InsuranceSettleVO.builder()
                .id(String.valueOf(entity.getId()))
                .settleNo(entity.getSettleNo())
                .chargeId(String.valueOf(entity.getChargeId()))
                .chargeNo(charge == null ? null : charge.getChargeNo())
                .patientId(charge == null ? null : charge.getPatientId())
                .patientName(patientName)
                .insuranceType(entity.getInsuranceType())
                .totalAmount(entity.getTotalAmount())
                .insuranceAmount(entity.getInsuranceAmount())
                .selfAmount(entity.getSelfAmount())
                .settleStatus(entity.getSettleStatus())
                .failReason(entity.getFailReason())
                .settleTime(entity.getSettleTime())
                .createTime(entity.getCreateTime())
                .build();
    }
}
