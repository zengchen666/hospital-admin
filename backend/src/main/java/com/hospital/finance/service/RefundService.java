package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.dto.RefundItemVO;
import com.hospital.finance.dto.RefundQuery;
import com.hospital.finance.dto.RefundRequest;
import com.hospital.finance.dto.RefundVO;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizChargeItem;
import com.hospital.finance.entity.BizRefund;
import com.hospital.finance.entity.BizRefundItem;
import com.hospital.finance.mapper.BizChargeItemMapper;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizRefundItemMapper;
import com.hospital.finance.mapper.BizRefundMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.pharmacy.entity.BizDispense;
import com.hospital.pharmacy.entity.BizDispenseItem;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.mapper.BizDispenseItemMapper;
import com.hospital.pharmacy.mapper.BizDispenseMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 退费 / 退药服务。
 *
 * <p><b>退费是医院里最容易出内部问题的操作</b>，所以这里刻意加了三道闸：</p>
 * <ol>
 *   <li><b>申请与审核必须两个人</b>（admin 豁免，但与处方审核、报告审核同一套逻辑）——
 *       收钱和退钱如果是同一个人，长年累月一定会出问题；</li>
 *   <li><b>防超退靠条件更新</b>，不靠先查后改（见 {@link BizChargeItemMapper#addRefundNum}）；</li>
 *   <li><b>金额以明细汇总为准</b>，与退费单总额对不上就拒绝 —— 宁可卡在窗口，
 *       也不能退一笔说不清来源的钱。</li>
 * </ol>
 *
 * <p>退药是退款执行时的联动动作：<b>钱退了，药就得回库</b>，
 * 且必须退回<b>原批号</b>（不同批号效期不同，退错批号会把效期账做乱）。</p>
 */
@Slf4j
@Service
public class RefundService {

    private static final int REFUND_PENDING = 0;
    private static final int REFUND_APPROVED = 1;
    private static final int REFUND_DONE = 2;
    private static final int REFUND_REJECTED = 3;

    private static final int PAY_STATUS_PAID = 1;
    private static final int SOURCE_PRESCRIPTION = 1;
    private static final int ITEM_TYPE_DRUG = 1;
    private static final int STOCK_RECORD_RETURN = 4;
    private static final String ADMIN_ROLE = "admin";

    private final BizRefundMapper refundMapper;
    private final BizRefundItemMapper refundItemMapper;
    private final BizChargeMapper chargeMapper;
    private final BizChargeItemMapper chargeItemMapper;
    private final BizDispenseMapper dispenseMapper;
    private final BizDispenseItemMapper dispenseItemMapper;
    private final BizStockMapper stockMapper;
    private final BizStockRecordMapper stockRecordMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final RefundNoGenerator noGenerator;

    public RefundService(BizRefundMapper refundMapper,
                         BizRefundItemMapper refundItemMapper,
                         BizChargeMapper chargeMapper,
                         BizChargeItemMapper chargeItemMapper,
                         BizDispenseMapper dispenseMapper,
                         BizDispenseItemMapper dispenseItemMapper,
                         BizStockMapper stockMapper,
                         BizStockRecordMapper stockRecordMapper,
                         BizPatientMapper patientMapper,
                         SysUserMapper userMapper,
                         RefundNoGenerator noGenerator) {
        this.refundMapper = refundMapper;
        this.refundItemMapper = refundItemMapper;
        this.chargeMapper = chargeMapper;
        this.chargeItemMapper = chargeItemMapper;
        this.dispenseMapper = dispenseMapper;
        this.dispenseItemMapper = dispenseItemMapper;
        this.stockMapper = stockMapper;
        this.stockRecordMapper = stockRecordMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 申请退费。
     *
     * <p>只建单、不动钱、不动库存 —— 真正改数据要等审核通过后的 {@link #complete}。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long apply(RefundRequest request) {
        BizCharge charge = chargeMapper.selectById(request.getChargeId());
        if (charge == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "原收费单不存在");
        }
        if (!Integer.valueOf(PAY_STATUS_PAID).equals(charge.getPayStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "未支付的收费单不能退费");
        }

        List<BizChargeItem> chargeItems = chargeItemMapper.selectList(
                Wrappers.<BizChargeItem>lambdaQuery()
                        .eq(BizChargeItem::getChargeId, charge.getId()));
        Map<Long, BizChargeItem> itemMap = new HashMap<>();
        for (BizChargeItem item : chargeItems) {
            itemMap.put(item.getId(), item);
        }

        // 逐项算钱：数量 × 原单价，不接受前端报价
        BigDecimal total = BigDecimal.ZERO;
        List<BizRefundItem> pending = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (RefundRequest.RefundItemRequest line : request.getItems()) {
            if (!seen.add(line.getChargeItemId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "同一收费明细不能重复退");
            }
            BizChargeItem source = itemMap.get(line.getChargeItemId());
            if (source == null) {
                throw new BusinessException(ErrorCode.NO_RESOURCE, "退费明细不属于该收费单");
            }
            int remain = source.getQuantity() - (source.getRefundNum() == null ? 0 : source.getRefundNum());
            if (line.getQuantity() > remain) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "「" + source.getItemName() + "」可退数量为 " + remain + "，本次申请 " + line.getQuantity());
            }
            BigDecimal unitPrice = source.getPrice() == null ? BigDecimal.ZERO : source.getPrice();
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(line.getQuantity()));

            BizRefundItem item = new BizRefundItem();
            item.setRefundId(null);
            item.setChargeItemId(source.getId());
            item.setItemType(source.getItemType());
            item.setItemName(source.getItemName());
            item.setQuantity(line.getQuantity());
            item.setAmount(amount);
            pending.add(item);
            total = total.add(amount);
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "退费金额必须大于 0");
        }
        if (total.compareTo(charge.getActualAmount()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "退费金额 " + total + " 超过原收费金额 " + charge.getActualAmount());
        }

        Long applicantId = CurrentUser.requireId();
        BizRefund refund = new BizRefund();
        refund.setRefundNo(noGenerator.next());
        refund.setChargeId(charge.getId());
        refund.setPatientId(charge.getPatientId());
        refund.setRefundAmount(total);
        refund.setRefundType(total.compareTo(charge.getActualAmount()) == 0 ? 1 : 2);
        refund.setReason(request.getReason());
        refund.setStatus(REFUND_PENDING);
        refund.setApplicantId(applicantId);
        refund.setOriginalPay(charge.getPayType());
        refundMapper.insert(refund);

        for (BizRefundItem item : pending) {
            item.setRefundId(refund.getId());
            refundItemMapper.insert(item);
        }

        log.info("退费申请已提交 refundNo={} chargeNo-amount={} amount={} items={}",
                refund.getRefundNo(), charge.getId(), total, pending.size());
        return refund.getId();
    }

    /**
     * 审核通过。
     *
     * <p><b>申请人不能审自己的单</b>（admin 豁免）。这是退费和收费之间
     * 唯一的结构性制衡 —— 少了它，一个人就能完成「收钱再退钱」的闭环。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        BizRefund refund = getExisting(id);
        if (!Integer.valueOf(REFUND_PENDING).equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "只有待审核的退费单可以审核");
        }
        Long auditorId = CurrentUser.requireId();
        LoginUser user = CurrentUser.orNull();
        if (!user.getRoleCodes().contains(ADMIN_ROLE)
                && Objects.equals(refund.getApplicantId(), auditorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能审核自己申请的退费");
        }
        if (refundMapper.approve(id, auditorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "退费单状态已变化，请刷新后重试");
        }
        log.info("退费审核通过 refundNo={} auditor={}", refund.getRefundNo(), auditorId);
    }

    /** 驳回：待审核或已审核都可以驳回，已退款的不行 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        BizRefund refund = getExisting(id);
        if (Integer.valueOf(REFUND_DONE).equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已退款的退费单不能驳回");
        }
        if (Integer.valueOf(REFUND_REJECTED).equals(refund.getStatus())) {
            return;
        }
        Long operatorId = CurrentUser.idOrNull();
        if (refundMapper.transitStatus(id, refund.getStatus(), REFUND_REJECTED, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "退费单状态已变化，请刷新后重试");
        }
        log.info("退费已驳回 refundNo={} reason={}", refund.getRefundNo(), reason);
    }

    /**
     * 执行退款：回冲已退数量，并按原批号把药退回库房。
     *
     * <p>三步必须同一事务：钱退了药没回库，等于白送；
     * 药回了库但明细没回冲，下次还能再退一次。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        BizRefund refund = getExisting(id);
        if (!Integer.valueOf(REFUND_APPROVED).equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "只有已审核的退费单可以退款");
        }

        List<BizRefundItem> items = refundItemMapper.selectList(
                Wrappers.<BizRefundItem>lambdaQuery().eq(BizRefundItem::getRefundId, id));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "退费单没有明细，无法退款");
        }

        Long operatorId = CurrentUser.requireId();
        for (BizRefundItem item : items) {
            // 条件更新兜住并发：超退时影响行数为 0
            if (chargeItemMapper.addRefundNum(item.getChargeItemId(), item.getQuantity()) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "「" + item.getItemName() + "」可退数量不足，请刷新后重试");
            }
        }

        // 退药：钱退了，药必须回库，且退回原批号
        returnDrugsToStock(refund.getChargeId(), items, operatorId);

        if (refundMapper.complete(id, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "退费单状态已变化，请刷新后重试");
        }
        log.info("退款完成 refundNo={} amount={} operator={}",
                refund.getRefundNo(), refund.getRefundAmount(), operatorId);
    }

    /**
     * 按原批号回退库存。
     *
     * <p>找不到对应发药记录就跳过（本期住院发药、检查收费都没有药品可退），
     * 但<b>只要退的是药品且发过药，就必须退回去</b> —— 否则账上退了钱、
     * 库房却少了药，盘点时对不上。</p>
     */
    private void returnDrugsToStock(Long chargeId, List<BizRefundItem> items, Long operatorId) {
        boolean hasDrug = items.stream()
                .anyMatch(i -> Integer.valueOf(ITEM_TYPE_DRUG).equals(i.getItemType()));
        if (!hasDrug) {
            return;
        }
        BizCharge charge = chargeMapper.selectById(chargeId);
        if (charge == null || !Integer.valueOf(SOURCE_PRESCRIPTION).equals(charge.getSourceType())) {
            return;
        }
        BizDispense dispense = dispenseMapper.selectDoneByPresc(charge.getSourceId());
        if (dispense == null) {
            log.warn("退费含药品但未找到已发药记录，跳过退药 chargeId={}", chargeId);
            return;
        }

        List<BizDispenseItem> dispensed = dispenseItemMapper.selectList(
                Wrappers.<BizDispenseItem>lambdaQuery()
                        .eq(BizDispenseItem::getDispenseId, dispense.getId()));
        Map<Long, BizDispenseItem> byDrug = new HashMap<>();
        for (BizDispenseItem d : dispensed) {
            byDrug.put(d.getDrugId(), d);
        }

        for (BizRefundItem item : items) {
            if (!Integer.valueOf(ITEM_TYPE_DRUG).equals(item.getItemType())) {
                continue;
            }
            BizChargeItem chargeItem = chargeItemMapper.selectById(item.getChargeItemId());
            if (chargeItem == null || chargeItem.getItemId() == null) {
                continue;
            }
            BizDispenseItem source = byDrug.get(chargeItem.getItemId());
            if (source == null) {
                log.warn("退药未找到发药明细，跳过 drugId={}", chargeItem.getItemId());
                continue;
            }
            // 退回到原批号：不同批号效期不同，退错会把效期账做乱
            int affected = stockMapper.increaseByBatch(
                    source.getDrugId(), dispense.getWarehouseId(), source.getBatchNo(), item.getQuantity());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "批次 " + source.getBatchNo() + " 不存在，无法退回库存，请联系药房处理");
            }

            BizStockRecord record = new BizStockRecord();
            record.setDrugId(source.getDrugId());
            record.setWarehouseId(dispense.getWarehouseId());
            record.setBatchNo(source.getBatchNo());
            record.setRecordType(STOCK_RECORD_RETURN);
            record.setQuantity(item.getQuantity());
            record.setRelatedNo(refundNoOf(item));
            stockRecordMapper.insert(record);
        }

        // 发药记录置为已退，避免同一张处方退两次药
        dispenseMapper.returnBack(dispense.getId(), operatorId);
    }

    private String refundNoOf(BizRefundItem item) {
        BizRefund refund = refundMapper.selectById(item.getRefundId());
        return refund == null ? null : refund.getRefundNo();
    }

    public PageResult<RefundVO> page(RefundQuery query) {
        LambdaQueryWrapper<BizRefund> wrapper = Wrappers.<BizRefund>lambdaQuery()
                .eq(query.getChargeId() != null, BizRefund::getChargeId, query.getChargeId())
                .eq(query.getPatientId() != null, BizRefund::getPatientId, query.getPatientId())
                .eq(query.getStatus() != null, BizRefund::getStatus, query.getStatus());
        if (query.getStartTime() != null) {
            wrapper.ge(BizRefund::getCreateTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizRefund::getCreateTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizRefund::getCreateTime);

        Page<BizRefund> page = refundMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Context context = loadContext(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, context));
    }

    public RefundVO detail(Long id) {
        BizRefund refund = getExisting(id);
        return toVO(refund, loadContext(List.of(refund)));
    }

    private BizRefund getExisting(Long id) {
        BizRefund refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "退费单不存在或已删除");
        }
        return refund;
    }

    private record Context(Map<Long, List<RefundItemVO>> items,
                           Map<Long, String> charges,
                           Map<Long, String> patients,
                           Map<Long, String> users) {
    }

    private Context loadContext(List<BizRefund> refunds) {
        if (refunds.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<Long> refundIds = refunds.stream().map(BizRefund::getId).collect(Collectors.toSet());
        Set<Long> chargeIds = refunds.stream().map(BizRefund::getChargeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> patientIds = refunds.stream().map(BizRefund::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = new java.util.HashSet<>();
        refunds.forEach(r -> {
            if (r.getApplicantId() != null) {
                userIds.add(r.getApplicantId());
            }
            if (r.getAuditorId() != null) {
                userIds.add(r.getAuditorId());
            }
        });

        Map<Long, List<RefundItemVO>> itemMap = new HashMap<>();
        for (BizRefundItem item : refundItemMapper.selectList(
                Wrappers.<BizRefundItem>lambdaQuery().in(BizRefundItem::getRefundId, refundIds))) {
            itemMap.computeIfAbsent(item.getRefundId(), k -> new ArrayList<>())
                    .add(RefundItemVO.builder()
                            .id(String.valueOf(item.getId()))
                            .chargeItemId(String.valueOf(item.getChargeItemId()))
                            .itemType(item.getItemType())
                            .itemName(item.getItemName())
                            .quantity(item.getQuantity())
                            .amount(item.getAmount())
                            .build());
        }

        Map<Long, String> charges = new HashMap<>();
        if (!chargeIds.isEmpty()) {
            for (BizCharge c : chargeMapper.selectByIds(chargeIds)) {
                charges.put(c.getId(), c.getChargeNo());
            }
        }
        Map<Long, String> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patients.put(p.getId(), p.getName());
            }
        }
        Map<Long, String> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(userIds)) {
                users.put(u.getId(), u.getRealName());
            }
        }
        return new Context(itemMap, charges, patients, users);
    }

    private RefundVO toVO(BizRefund entity, Context context) {
        return RefundVO.builder()
                .id(String.valueOf(entity.getId()))
                .refundNo(entity.getRefundNo())
                .chargeId(String.valueOf(entity.getChargeId()))
                .chargeNo(context.charges().get(entity.getChargeId()))
                .patientId(entity.getPatientId())
                .patientName(context.patients().get(entity.getPatientId()))
                .refundAmount(entity.getRefundAmount())
                .refundType(entity.getRefundType())
                .reason(entity.getReason())
                .status(entity.getStatus())
                .applicantId(entity.getApplicantId())
                .applicantName(context.users().get(entity.getApplicantId()))
                .auditorId(entity.getAuditorId())
                .auditorName(context.users().get(entity.getAuditorId()))
                .auditTime(entity.getAuditTime())
                .refundTime(entity.getRefundTime())
                .createTime(entity.getCreateTime())
                .items(context.items().getOrDefault(entity.getId(), List.of()))
                .build();
    }
}
