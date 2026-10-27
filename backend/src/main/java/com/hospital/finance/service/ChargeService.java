package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.clinic.entity.BizPrescription;
import com.hospital.clinic.entity.BizPrescriptionItem;
import com.hospital.clinic.mapper.BizPrescriptionItemMapper;
import com.hospital.clinic.mapper.BizPrescriptionMapper;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.finance.dto.ChargeItemVO;
import com.hospital.finance.dto.ChargeQuery;
import com.hospital.finance.dto.ChargeRequest;
import com.hospital.finance.dto.ChargeResult;
import com.hospital.finance.dto.ChargeVO;
import com.hospital.exam.entity.BizExamApply;
import com.hospital.exam.entity.BizExamItem;
import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamItemMapper;
import com.hospital.finance.dto.ExamChargeRequest;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizChargeItem;
import com.hospital.finance.entity.BizTreatItem;
import com.hospital.finance.mapper.BizChargeItemMapper;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizTreatItemMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收费服务。
 *
 * <p><b>防重复收费双保险</b>：</p>
 * <ol>
 *   <li>数据库层：{@code uk_charge_source(source_type, source_id)} 唯一索引，
 *       并发下第二张单直接撞键；</li>
 *   <li>业务层：先校验处方状态，再在收费成功后把处方 1 → 2。</li>
 * </ol>
 *
 * <p><b>金额自洽</b>：收的是「处方明细汇总」，不是「处方表上的总额」——
 * 两者比对不上说明数据被人动过，宁可报错也不能收错钱。</p>
 */
@Slf4j
@Service
public class ChargeService {

    private static final int PRESC_PENDING_AUDIT = 0;
    private static final int PRESC_AUDITED = 1;
    private static final int PRESC_CHARGED = 2;
    private static final int PRESC_CANCELLED = 4;

    private static final int SOURCE_PRESCRIPTION = 1;
    private static final int SOURCE_EXAM = 2;
    private static final int CHARGE_TYPE_OUTPATIENT = 2;
    private static final int PAY_STATUS_PAID = 1;

    /** biz_charge_item.item_type：1 药品 / 2 诊疗 / 3 检查 / 4 床位 */
    private static final int ITEM_TYPE_EXAM = 3;

    /** biz_exam_apply.status：0 待缴费 / 1 待采样 */
    private static final int EXAM_UNPAID = 0;
    private static final int EXAM_WAITING_SAMPLE = 1;

    private final BizChargeMapper chargeMapper;
    private final BizChargeItemMapper chargeItemMapper;
    private final BizPrescriptionMapper prescriptionMapper;
    private final BizPrescriptionItemMapper prescriptionItemMapper;
    private final BizTreatItemMapper treatItemMapper;
    private final BizExamApplyMapper examApplyMapper;
    private final BizExamItemMapper examItemMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final ChargeNoGenerator noGenerator;

    public ChargeService(BizChargeMapper chargeMapper,
                         BizChargeItemMapper chargeItemMapper,
                         BizPrescriptionMapper prescriptionMapper,
                         BizPrescriptionItemMapper prescriptionItemMapper,
                         BizTreatItemMapper treatItemMapper,
                         BizExamApplyMapper examApplyMapper,
                         BizExamItemMapper examItemMapper,
                         BizPatientMapper patientMapper,
                         SysUserMapper userMapper,
                         ChargeNoGenerator noGenerator) {
        this.chargeMapper = chargeMapper;
        this.chargeItemMapper = chargeItemMapper;
        this.prescriptionMapper = prescriptionMapper;
        this.prescriptionItemMapper = prescriptionItemMapper;
        this.treatItemMapper = treatItemMapper;
        this.examApplyMapper = examApplyMapper;
        this.examItemMapper = examItemMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 处方收费：现场一次完成（建单 + 收款）。
     *
     * <p>预交金、扫码支付等异步场景再拆成「建单 → 支付」两步，
     * 一期按收费窗口的实际操作走一步到位。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeResult chargePrescription(ChargeRequest request) {
        BizPrescription prescription = prescriptionMapper.selectById(request.getPrescId());
        if (prescription == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "处方不存在");
        }
        if (Integer.valueOf(PRESC_CANCELLED).equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方已作废，不能收费");
        }
        if (Integer.valueOf(PRESC_PENDING_AUDIT).equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方尚未审核，不能收费");
        }
        if (Integer.valueOf(PRESC_CHARGED).equals(prescription.getStatus())
                || prescription.getStatus() > PRESC_CHARGED) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该处方已收费，请勿重复操作");
        }

        List<BizPrescriptionItem> items = prescriptionItemMapper.selectList(
                Wrappers.<BizPrescriptionItem>lambdaQuery()
                        .eq(BizPrescriptionItem::getPrescId, prescription.getId()));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方没有药品明细，无法收费");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (BizPrescriptionItem item : items) {
            total = total.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
        }
        // 明细汇总与处方总额必须一致，不一致说明中间被人改过数据
        if (prescription.getTotalAmount() == null || total.compareTo(prescription.getTotalAmount()) != 0) {
            log.error("处方金额与明细不一致 prescNo={} prescTotal={} itemTotal={}",
                    prescription.getPrescNo(), prescription.getTotalAmount(), total);
            throw new BusinessException(ErrorCode.CONFLICT, "处方金额与明细不一致，请核对后重试");
        }

        BigDecimal discount = request.getDiscountAmount() == null
                ? BigDecimal.ZERO : request.getDiscountAmount();
        if (discount.compareTo(total) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "优惠金额不能超过应收金额");
        }
        BigDecimal actual = total.subtract(discount);

        Long operatorId = CurrentUser.requireId();
        BizCharge charge = new BizCharge();
        charge.setChargeNo(noGenerator.next());
        charge.setPatientId(prescription.getPatientId());
        charge.setVisitId(prescription.getVisitId());
        charge.setSourceType(SOURCE_PRESCRIPTION);
        charge.setSourceId(prescription.getId());
        charge.setChargeType(CHARGE_TYPE_OUTPATIENT);
        charge.setTotalAmount(total);
        charge.setDiscountAmount(discount);
        charge.setActualAmount(actual);
        charge.setPayType(request.getPayType());
        charge.setPayStatus(PAY_STATUS_PAID);
        charge.setOperatorId(operatorId);
        charge.setChargeTime(LocalDateTime.now());

        try {
            chargeMapper.insert(charge);
        } catch (DuplicateKeyException e) {
            // uk_charge_source：同一张处方已经收过费
            throw new BusinessException(ErrorCode.CONFLICT, "该处方已收费，请勿重复操作");
        }

        List<BizChargeItem> chargeItems = new ArrayList<>(items.size());
        for (BizPrescriptionItem item : items) {
            BizChargeItem entity = new BizChargeItem();
            entity.setChargeId(charge.getId());
            entity.setItemType(1);
            entity.setItemId(item.getDrugId());
            entity.setItemName(item.getDrugName());
            entity.setQuantity(item.getQuantity());
            entity.setPrice(item.getPrice());
            entity.setAmount(item.getAmount());
            entity.setRefundNum(0);
            chargeItemMapper.insert(entity);
            chargeItems.add(entity);
        }

        // 处方 1 → 2，条件更新兜住并发
        int affected = prescriptionMapper.transitStatus(
                prescription.getId(), PRESC_AUDITED, PRESC_CHARGED, operatorId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "处方状态已变化，请刷新后重试");
        }

        log.info("收费成功 chargeNo={} prescNo={} total={} actual={}",
                charge.getChargeNo(), prescription.getPrescNo(), total, actual);

        return ChargeResult.builder()
                .id(String.valueOf(charge.getId()))
                .chargeNo(charge.getChargeNo())
                .totalAmount(total)
                .discountAmount(discount)
                .actualAmount(actual)
                .payStatus(charge.getPayStatus())
                .build();
    }

    /**
     * 检查检验收费：收完费才把检查单推进到「待采样」。
     *
     * <p><b>金额按价目表算，不接受前端报价</b>：逐条检查明细去
     * {@code biz_treat_item} 取当前生效价（注意取「生效日期最新的一版」，
     * 价格是有版本的）。取不到价就拒绝收费 —— 宁可让业务停在窗口，
     * 也不能按 0 元把单子放过去，那等于白做一次检查。</p>
     *
     * <p>与处方收费共用 {@code uk_charge_source} 防重复：同一张检查单
     * 只能出一张收费单，并发下第二笔直接撞唯一键。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeResult chargeExam(ExamChargeRequest request) {
        BizExamApply apply = examApplyMapper.selectById(request.getApplyId());
        if (apply == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "检查单不存在");
        }
        if (!Integer.valueOf(EXAM_UNPAID).equals(apply.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该检查单已缴费，请勿重复操作");
        }

        List<BizExamItem> items = examItemMapper.selectList(Wrappers.<BizExamItem>lambdaQuery()
                .eq(BizExamItem::getApplyId, apply.getId())
                .orderByAsc(BizExamItem::getSort));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "检查单没有项目明细，无法收费");
        }

        // 逐项取价：任一项没有生效价就整单拒绝，不做「跳过」或「按 0 计」
        BigDecimal total = BigDecimal.ZERO;
        List<PricedItem> priced = new ArrayList<>(items.size());
        for (BizExamItem item : items) {
            BizTreatItem price = treatItemMapper.selectEffective(item.getItemCode());
            if (price == null || price.getPrice() == null) {
                log.error("检查项目未定价 applyId={} itemCode={} itemName={}",
                        apply.getId(), item.getItemCode(), item.getItemName());
                throw new BusinessException(ErrorCode.NO_RESOURCE,
                        "检查项目「" + item.getItemName() + "」未维护价格，请先到价目表维护");
            }
            total = total.add(price.getPrice());
            priced.add(new PricedItem(item, price));
        }

        BigDecimal discount = request.getDiscountAmount() == null
                ? BigDecimal.ZERO : request.getDiscountAmount();
        if (discount.compareTo(total) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "优惠金额不能超过应收金额");
        }
        BigDecimal actual = total.subtract(discount);

        Long operatorId = CurrentUser.requireId();
        BizCharge charge = new BizCharge();
        charge.setChargeNo(noGenerator.next());
        charge.setPatientId(apply.getPatientId());
        charge.setVisitId(apply.getVisitId());
        charge.setSourceType(SOURCE_EXAM);
        charge.setSourceId(apply.getId());
        charge.setChargeType(CHARGE_TYPE_OUTPATIENT);
        charge.setTotalAmount(total);
        charge.setDiscountAmount(discount);
        charge.setActualAmount(actual);
        charge.setPayType(request.getPayType());
        charge.setPayStatus(PAY_STATUS_PAID);
        charge.setOperatorId(operatorId);
        charge.setChargeTime(LocalDateTime.now());

        try {
            chargeMapper.insert(charge);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "该检查单已收费，请勿重复操作");
        }

        for (PricedItem pricedItem : priced) {
            BizChargeItem entity = new BizChargeItem();
            entity.setChargeId(charge.getId());
            entity.setItemType(ITEM_TYPE_EXAM);
            entity.setItemId(pricedItem.price().getId());
            entity.setItemName(pricedItem.price().getItemName());
            entity.setQuantity(1);
            entity.setPrice(pricedItem.price().getPrice());
            entity.setAmount(pricedItem.price().getPrice());
            entity.setRefundNum(0);
            chargeItemMapper.insert(entity);
        }

        // 检查单 0 → 1 待采样，条件更新兜住并发
        if (examApplyMapper.transitStatus(apply.getId(), EXAM_UNPAID, EXAM_WAITING_SAMPLE, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "检查单状态已变化，请刷新后重试");
        }

        log.info("检查收费成功 chargeNo={} applyNo={} items={} total={} actual={}",
                charge.getChargeNo(), apply.getApplyNo(), priced.size(), total, actual);

        return ChargeResult.builder()
                .id(String.valueOf(charge.getId()))
                .chargeNo(charge.getChargeNo())
                .totalAmount(total)
                .discountAmount(discount)
                .actualAmount(actual)
                .payStatus(charge.getPayStatus())
                .build();
    }

    /** 检查明细与其取到的价目，避免二次查库 */
    private record PricedItem(BizExamItem item, BizTreatItem price) {
    }

    public PageResult<ChargeVO> page(ChargeQuery query) {
        LambdaQueryWrapper<BizCharge> wrapper = Wrappers.<BizCharge>lambdaQuery()
                .eq(query.getPatientId() != null, BizCharge::getPatientId, query.getPatientId())
                .eq(query.getVisitId() != null, BizCharge::getVisitId, query.getVisitId())
                .eq(query.getOperatorId() != null, BizCharge::getOperatorId, query.getOperatorId())
                .eq(query.getChargeType() != null, BizCharge::getChargeType, query.getChargeType())
                .eq(query.getPayStatus() != null, BizCharge::getPayStatus, query.getPayStatus())
                .eq(query.getPayType() != null, BizCharge::getPayType, query.getPayType())
                .eq(query.getSourceType() != null, BizCharge::getSourceType, query.getSourceType());

        if (StringUtils.hasText(query.getChargeNo())) {
            wrapper.likeRight(BizCharge::getChargeNo, query.getChargeNo().trim());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(BizCharge::getChargeTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizCharge::getChargeTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizCharge::getChargeTime);

        Page<BizCharge> page = chargeMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        Map<Long, List<ChargeItemVO>> itemMap = loadItems(page.getRecords());
        Names names = loadNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, itemMap, names));
    }

    public ChargeVO detail(Long id) {
        BizCharge entity = chargeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "收费单不存在或已删除");
        }
        return toVO(entity, loadItems(List.of(entity)), loadNames(List.of(entity)));
    }

    private Map<Long, List<ChargeItemVO>> loadItems(List<BizCharge> charges) {
        if (charges.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = charges.stream().map(BizCharge::getId).collect(Collectors.toSet());
        List<BizChargeItem> items = chargeItemMapper.selectList(
                Wrappers.<BizChargeItem>lambdaQuery().in(BizChargeItem::getChargeId, ids));

        Map<Long, List<ChargeItemVO>> result = new HashMap<>();
        for (BizChargeItem item : items) {
            result.computeIfAbsent(item.getChargeId(), k -> new ArrayList<>())
                    .add(ChargeItemVO.builder()
                            .id(String.valueOf(item.getId()))
                            .itemType(item.getItemType())
                            .itemId(item.getItemId())
                            .itemName(item.getItemName())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .amount(item.getAmount())
                            .refundNum(item.getRefundNum())
                            .build());
        }
        return result;
    }

    private record Names(Map<Long, String> patients, Map<Long, String> operators) {
    }

    private Names loadNames(List<BizCharge> charges) {
        Set<Long> patientIds = charges.stream().map(BizCharge::getPatientId).collect(Collectors.toSet());
        Set<Long> operatorIds = charges.stream()
                .map(BizCharge::getOperatorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patients.put(p.getId(), p.getName());
            }
        }
        Map<Long, String> operators = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(operatorIds)) {
                operators.put(u.getId(), u.getRealName());
            }
        }
        return new Names(patients, operators);
    }

    private ChargeVO toVO(BizCharge entity, Map<Long, List<ChargeItemVO>> itemMap, Names names) {
        return ChargeVO.builder()
                .id(String.valueOf(entity.getId()))
                .chargeNo(entity.getChargeNo())
                .patientId(entity.getPatientId())
                .patientName(names.patients().get(entity.getPatientId()))
                .visitId(entity.getVisitId())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId() == null ? null : String.valueOf(entity.getSourceId()))
                .chargeType(entity.getChargeType())
                .totalAmount(entity.getTotalAmount())
                .discountAmount(entity.getDiscountAmount())
                .actualAmount(entity.getActualAmount())
                .payType(entity.getPayType())
                .payStatus(entity.getPayStatus())
                .operatorId(entity.getOperatorId())
                .operatorName(names.operators().get(entity.getOperatorId()))
                .chargeTime(entity.getChargeTime())
                .items(itemMap.getOrDefault(entity.getId(), List.of()))
                .build();
    }
}
