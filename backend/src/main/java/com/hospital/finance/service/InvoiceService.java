package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.finance.dto.InvoiceQuery;
import com.hospital.finance.dto.InvoiceVO;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizInvoice;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizInvoiceMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发票服务：开票、作废、查询。
 *
 * <p><b>一张收费单只能有一张有效发票</b>：重复开票会让同一笔收入对应两张票，
 * 报税时直接多算一份。作废之后可以重开 —— 作废不是「这笔没开过票」，
 * 而是「这张票不算数了」，顺序上必须先废后开。</p>
 *
 * <p>金额取收费单实收金额快照，不跟着药品调价变：
 * 发票开出去那一刻就定了，患者手上的票和医院账上的数必须始终一致。</p>
 */
@Slf4j
@Service
public class InvoiceService {

    private static final int NORMAL = 0;
    private static final int VOIDED = 1;

    private final BizInvoiceMapper invoiceMapper;
    private final BizChargeMapper chargeMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final InvoiceNoGenerator noGenerator;

    public InvoiceService(BizInvoiceMapper invoiceMapper,
                          BizChargeMapper chargeMapper,
                          BizPatientMapper patientMapper,
                          SysUserMapper userMapper,
                          InvoiceNoGenerator noGenerator) {
        this.invoiceMapper = invoiceMapper;
        this.chargeMapper = chargeMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 开票（电子 / 纸质）。
     *
     * <p>未支付（{@code pay_status = 0}）的单不能开票：
     * 票是收款凭证，钱还没到就开票，等于替患者向税务承诺了一笔没发生的收入。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long print(Long chargeId, Integer invoiceType) {
        BizCharge charge = chargeMapper.selectById(chargeId);
        if (charge == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "收费单不存在");
        }
        if (charge.getPayStatus() == null || charge.getPayStatus() == 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该收费单尚未支付，不能开票");
        }
        BizInvoice valid = invoiceMapper.selectValidByCharge(chargeId);
        if (valid != null) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                    "该收费单已开过发票 " + valid.getInvoiceNo() + "，请先作废再重开");
        }

        BizInvoice entity = new BizInvoice();
        entity.setInvoiceNo(noGenerator.next());
        entity.setChargeId(chargeId);
        entity.setInvoiceType(invoiceType);
        entity.setAmount(nvl(charge.getActualAmount()));
        entity.setStatus(NORMAL);
        entity.setPrintTime(LocalDateTime.now());
        invoiceMapper.insert(entity);
        log.info("开票成功 invoiceNo={} chargeId={} amount={} operator={}",
                entity.getInvoiceNo(), chargeId, entity.getAmount(), CurrentUser.idOrNull());
        return entity.getId();
    }

    /**
     * 作废。
     *
     * <p>物理删除是绝对禁止的：发票号已报税，删掉之后
     * 「这张票去哪了」永远查不到，税务来查时无从解释。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id) {
        BizInvoice entity = invoiceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "发票不存在");
        }
        if (invoiceMapper.voidInvoice(id) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "发票状态已变化，请刷新后重试");
        }
        log.warn("发票作废 invoiceNo={} chargeId={} operator={}",
                entity.getInvoiceNo(), entity.getChargeId(), CurrentUser.idOrNull());
    }

    public InvoiceVO detail(Long id) {
        BizInvoice entity = invoiceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "发票不存在");
        }
        return toVO(entity, loadContext(List.of(entity)));
    }

    public PageResult<InvoiceVO> page(InvoiceQuery query) {
        LambdaQueryWrapper<BizInvoice> wrapper = Wrappers.<BizInvoice>lambdaQuery()
                .eq(query.getInvoiceType() != null, BizInvoice::getInvoiceType, query.getInvoiceType())
                .eq(query.getStatus() != null, BizInvoice::getStatus, query.getStatus());

        if (StringUtils.hasText(query.getInvoiceNo())) {
            wrapper.likeRight(BizInvoice::getInvoiceNo, query.getInvoiceNo().trim());
        }
        // 按收费单号查要先反查收费单：发票表只存 charge_id
        if (StringUtils.hasText(query.getChargeNo())) {
            List<Long> chargeIds = chargeMapper.selectList(Wrappers.<BizCharge>lambdaQuery()
                            .likeRight(BizCharge::getChargeNo, query.getChargeNo().trim()))
                    .stream().map(BizCharge::getId).toList();
            if (chargeIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(BizInvoice::getChargeId, chargeIds);
        }
        if (query.getPatientId() != null) {
            List<Long> chargeIds = chargeMapper.selectList(Wrappers.<BizCharge>lambdaQuery()
                            .eq(BizCharge::getPatientId, query.getPatientId()))
                    .stream().map(BizCharge::getId).toList();
            if (chargeIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(BizInvoice::getChargeId, chargeIds);
        }
        wrapper.orderByDesc(BizInvoice::getPrintTime);

        Page<BizInvoice> page = invoiceMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Context context = loadContext(page.getRecords());
        return PageResult.of(page, e -> toVO(e, context));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Context(Map<Long, BizCharge> charges,
                           Map<Long, String> patients,
                           Map<Long, String> users) {
    }

    private Context loadContext(List<BizInvoice> invoices) {
        if (invoices.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of());
        }
        Set<Long> chargeIds = invoices.stream().map(BizInvoice::getChargeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizCharge> charges = new HashMap<>();
        if (!chargeIds.isEmpty()) {
            for (BizCharge c : chargeMapper.selectByIds(chargeIds)) {
                charges.put(c.getId(), c);
            }
        }
        Set<Long> patientIds = charges.values().stream().map(BizCharge::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patients.put(p.getId(), p.getName());
            }
        }
        Set<Long> userIds = invoices.stream().map(BizInvoice::getCreateBy)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(userIds)) {
                users.put(u.getId(), u.getRealName());
            }
        }
        return new Context(charges, patients, users);
    }

    private InvoiceVO toVO(BizInvoice entity, Context context) {
        BizCharge charge = context.charges().get(entity.getChargeId());
        return InvoiceVO.builder()
                .id(String.valueOf(entity.getId()))
                .invoiceNo(entity.getInvoiceNo())
                .chargeId(String.valueOf(entity.getChargeId()))
                .chargeNo(charge == null ? null : charge.getChargeNo())
                .patientId(charge == null ? null : charge.getPatientId())
                .patientName(charge == null ? null : context.patients().get(charge.getPatientId()))
                .invoiceType(entity.getInvoiceType())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .printTime(entity.getPrintTime())
                .operatorName(context.users().get(entity.getCreateBy()))
                .createTime(entity.getCreateTime())
                .build();
    }
}
