package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.dto.RefundRequest;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizChargeItem;
import com.hospital.finance.entity.BizRefund;
import com.hospital.finance.entity.BizRefundItem;
import com.hospital.finance.mapper.BizChargeItemMapper;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizRefundItemMapper;
import com.hospital.finance.mapper.BizRefundMapper;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.pharmacy.entity.BizDispense;
import com.hospital.pharmacy.entity.BizDispenseItem;
import com.hospital.pharmacy.mapper.BizDispenseItemMapper;
import com.hospital.pharmacy.mapper.BizDispenseMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
import com.hospital.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退费 / 退药：防超退、不能自审、金额对账、按原批号回库。
 *
 * <p>这里守的是三件事：<b>不能多退一分钱、不能自己审自己、退了钱药必须回库</b>。
 * 前两条是内部风险，第三条是库存账实相符 —— 三条任一失守都是真金白银的损失。</p>
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long CHARGE_ID = 4001L;
    private static final Long REFUND_ID = 4501L;
    private static final Long PATIENT_ID = 100L;
    private static final Long APPLICANT_ID = 5L;
    private static final Long AUDITOR_ID = 7L;

    @Mock
    private BizRefundMapper refundMapper;
    @Mock
    private BizRefundItemMapper refundItemMapper;
    @Mock
    private BizChargeMapper chargeMapper;
    @Mock
    private BizChargeItemMapper chargeItemMapper;
    @Mock
    private BizDispenseMapper dispenseMapper;
    @Mock
    private BizDispenseItemMapper dispenseItemMapper;
    @Mock
    private BizStockMapper stockMapper;
    @Mock
    private BizStockRecordMapper stockRecordMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private RefundNoGenerator noGenerator;

    private RefundService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizRefund.class);
        TableInfoHelper.initTableInfo(assistant, BizRefundItem.class);
        TableInfoHelper.initTableInfo(assistant, BizCharge.class);
        TableInfoHelper.initTableInfo(assistant, BizChargeItem.class);
        TableInfoHelper.initTableInfo(assistant, BizDispense.class);
        TableInfoHelper.initTableInfo(assistant, BizDispenseItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new RefundService(refundMapper, refundItemMapper, chargeMapper, chargeItemMapper,
                dispenseMapper, dispenseItemMapper, stockMapper, stockRecordMapper,
                patientMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizRefund entity = inv.getArgument(0);
            entity.setId(REFUND_ID);
            return 1;
        }).when(refundMapper).insert(any(BizRefund.class));
        lenient().when(noGenerator.next()).thenReturn("TF202610270001");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("user" + userId)
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizCharge charge(Integer payStatus, String actualAmount) {
        BizCharge charge = new BizCharge();
        charge.setId(CHARGE_ID);
        charge.setChargeNo("CHG202610260001");
        charge.setPatientId(PATIENT_ID);
        charge.setPayStatus(payStatus);
        charge.setActualAmount(new BigDecimal(actualAmount));
        charge.setSourceType(1);
        charge.setSourceId(5001L);
        return charge;
    }

    private BizChargeItem chargeItem(Long id, String name, int quantity, String price, int refunded) {
        BizChargeItem item = new BizChargeItem();
        item.setId(id);
        item.setChargeId(CHARGE_ID);
        item.setItemType(1);
        item.setItemId(9101L);
        item.setItemName(name);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal(price));
        item.setAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        item.setRefundNum(refunded);
        return item;
    }

    private RefundRequest request(Long... pairs) {
        RefundRequest request = new RefundRequest();
        request.setChargeId(CHARGE_ID);
        request.setReason("患者要求退药");
        List<RefundRequest.RefundItemRequest> lines = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            RefundRequest.RefundItemRequest line = new RefundRequest.RefundItemRequest();
            line.setChargeItemId(pairs[i]);
            line.setQuantity(pairs[i + 1].intValue());
            lines.add(line);
        }
        request.setItems(lines);
        return request;
    }

    private BizRefund refund(Integer status, Long applicantId, String amount) {
        BizRefund refund = new BizRefund();
        refund.setId(REFUND_ID);
        refund.setRefundNo("TF202610270001");
        refund.setChargeId(CHARGE_ID);
        refund.setPatientId(PATIENT_ID);
        refund.setRefundAmount(new BigDecimal(amount));
        refund.setStatus(status);
        refund.setApplicantId(applicantId);
        return refund;
    }

    private BizRefundItem refundItem(Long id, Long chargeItemId, String name, int qty, String amount) {
        BizRefundItem item = new BizRefundItem();
        item.setId(id);
        item.setRefundId(REFUND_ID);
        item.setChargeItemId(chargeItemId);
        item.setItemType(1);
        item.setItemName(name);
        item.setQuantity(qty);
        item.setAmount(new BigDecimal(amount));
        return item;
    }

    /* ---------------- 申请 ---------------- */

    @Test
    @DisplayName("收费单不存在时报 40400")
    void chargeNotFound() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(null);
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.apply(request(4101L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原收费单不存在");
    }

    @Test
    @DisplayName("未支付的收费单不能退费")
    void unpaidChargeCannotBeRefunded() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(0, "36.00"));
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.apply(request(4101L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未支付");

        verify(refundMapper, never()).insert(any(BizRefund.class));
    }

    @Test
    @DisplayName("退数量超过可退数量时被拒（防超退第一道闸）")
    void overRefundIsRejected() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "72.00"));
        // 原数量 2，已退 1 → 只剩 1 可退
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(chargeItem(4101L, "阿莫西林胶囊", 2, "18.00", 1)));
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.apply(request(4101L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可退数量为 1");

        verify(refundMapper, never()).insert(any(BizRefund.class));
    }

    @Test
    @DisplayName("同一收费明细不能在一次申请里重复退")
    void duplicateItemIsRejected() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "72.00"));
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(chargeItem(4101L, "阿莫西林胶囊", 4, "18.00", 0)));
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.apply(request(4101L, 1L, 4101L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复退");
    }

    @Test
    @DisplayName("退费明细不属于该收费单时被拒（防串单）")
    void foreignChargeItemIsRejected() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "72.00"));
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(chargeItem(4101L, "阿莫西林胶囊", 2, "18.00", 0)));
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.apply(request(9999L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该收费单");
    }

    @Test
    @DisplayName("退费金额超过原收费金额时被拒")
    void refundExceedsChargeIsRejected() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "36.00"));
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(chargeItem(4101L, "阿莫西林胶囊", 10, "18.00", 0)));
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.apply(request(4101L, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过原收费金额");
    }

    @Test
    @DisplayName("申请成功：金额按原单价算，全额退标记 refundType=1")
    void applyCalculatesAmountFromUnitPrice() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "36.00"));
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(chargeItem(4101L, "阿莫西林胶囊", 2, "18.00", 0)));
        loginAs(APPLICANT_ID, "cashier");

        Long id = service.apply(request(4101L, 2L));

        assertThat(id).isEqualTo(REFUND_ID);
        ArgumentCaptor<BizRefund> captor = ArgumentCaptor.forClass(BizRefund.class);
        verify(refundMapper).insert(captor.capture());
        BizRefund saved = captor.getValue();
        // 2 × 18 = 36，与实收一致 → 全额退
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("36.00");
        assertThat(saved.getRefundType()).isEqualTo(1);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getApplicantId()).isEqualTo(APPLICANT_ID);

        verify(refundItemMapper).insert(any(BizRefundItem.class));
    }

    @Test
    @DisplayName("部分退时 refundType=2")
    void partialRefundMarkedAsType2() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "36.00"));
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(chargeItem(4101L, "阿莫西林胶囊", 2, "18.00", 0)));
        loginAs(APPLICANT_ID, "cashier");

        service.apply(request(4101L, 1L));

        ArgumentCaptor<BizRefund> captor = ArgumentCaptor.forClass(BizRefund.class);
        verify(refundMapper).insert(captor.capture());
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("18.00");
        assertThat(captor.getValue().getRefundType()).isEqualTo(2);
    }

    /* ---------------- 审核 ---------------- */

    @Test
    @DisplayName("申请人不能审核自己提交的退费（40301）")
    void applicantCannotApproveOwnRefund() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(0, APPLICANT_ID, "36.00"));
        loginAs(APPLICANT_ID, "cashier");

        assertThatThrownBy(() -> service.approve(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能审核自己申请");

        verify(refundMapper, never()).approve(anyLong(), anyLong());
    }

    @Test
    @DisplayName("admin 可以审核自己提交的单（豁免）")
    void adminCanApproveOwnRefund() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(0, 1L, "36.00"));
        when(refundMapper.approve(REFUND_ID, 1L)).thenReturn(1);
        loginAs(1L, "admin");

        service.approve(REFUND_ID);

        verify(refundMapper).approve(REFUND_ID, 1L);
    }

    @Test
    @DisplayName("他人审核通过并记录审核人")
    void otherUserCanApprove() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(0, APPLICANT_ID, "36.00"));
        when(refundMapper.approve(REFUND_ID, AUDITOR_ID)).thenReturn(1);
        loginAs(AUDITOR_ID, "finance");

        service.approve(REFUND_ID);

        verify(refundMapper).approve(REFUND_ID, AUDITOR_ID);
    }

    @Test
    @DisplayName("非待审核状态不能审核")
    void onlyPendingCanBeApproved() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(2, APPLICANT_ID, "36.00"));
        loginAs(AUDITOR_ID, "finance");

        assertThatThrownBy(() -> service.approve(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有待审核");
    }

    @Test
    @DisplayName("并发下审核失败返回冲突")
    void approveConflict() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(0, APPLICANT_ID, "36.00"));
        when(refundMapper.approve(anyLong(), anyLong())).thenReturn(0);
        loginAs(AUDITOR_ID, "finance");

        assertThatThrownBy(() -> service.approve(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变化");
    }

    /* ---------------- 驳回 ---------------- */

    @Test
    @DisplayName("已退款的单不能驳回")
    void doneRefundCannotBeRejected() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(2, APPLICANT_ID, "36.00"));
        loginAs(AUDITOR_ID, "finance");

        assertThatThrownBy(() -> service.reject(REFUND_ID, "不通过"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已退款");
    }

    @Test
    @DisplayName("已驳回的单重复驳回直接返回，不报错（幂等）")
    void rejectTwiceIsIdempotent() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(3, APPLICANT_ID, "36.00"));
        loginAs(AUDITOR_ID, "finance");

        service.reject(REFUND_ID, "不通过");

        verify(refundMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    /* ---------------- 执行退款 ---------------- */

    @Test
    @DisplayName("只有已审核的退费单能执行退款")
    void onlyApprovedCanComplete() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(0, APPLICANT_ID, "36.00"));
        loginAs(AUDITOR_ID, "finance");

        assertThatThrownBy(() -> service.complete(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有已审核");
    }

    @Test
    @DisplayName("退款执行：回冲已退数量，并按原批号把药退回库房")
    void completeReturnsDrugToOriginalBatch() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(1, APPLICANT_ID, "36.00"));
        when(refundItemMapper.selectList(any()))
                .thenReturn(List.of(refundItem(4501L, 4101L, "阿莫西林胶囊", 2, "36.00")));
        when(chargeItemMapper.addRefundNum(4101L, 2)).thenReturn(1);
        when(chargeItemMapper.selectById(4101L)).thenReturn(chargeItem(4101L, "阿莫西林胶囊", 2, "18.00", 0));
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "36.00"));

        BizDispense dispense = new BizDispense();
        dispense.setId(3001L);
        dispense.setWarehouseId(9201L);
        when(dispenseMapper.selectDoneByPresc(5001L)).thenReturn(dispense);

        BizDispenseItem dispensed = new BizDispenseItem();
        dispensed.setDrugId(9101L);
        dispensed.setBatchNo("BATCH001");
        when(dispenseItemMapper.selectList(any())).thenReturn(List.of(dispensed));
        when(stockMapper.increaseByBatch(eq(9101L), eq(9201L), eq("BATCH001"), eq(2))).thenReturn(1);
        when(refundMapper.complete(REFUND_ID, AUDITOR_ID)).thenReturn(1);
        loginAs(AUDITOR_ID, "finance");

        service.complete(REFUND_ID);

        verify(chargeItemMapper).addRefundNum(4101L, 2);
        // 必须退回原批号：退错批号会把效期账做乱
        verify(stockMapper).increaseByBatch(9101L, 9201L, "BATCH001", 2);
        verify(stockRecordMapper).insert(any(com.hospital.pharmacy.entity.BizStockRecord.class));
        verify(dispenseMapper).returnBack(3001L, AUDITOR_ID);
        verify(refundMapper).complete(REFUND_ID, AUDITOR_ID);
    }

    @Test
    @DisplayName("并发导致可退数量不足时整单回滚，不能只回冲一半")
    void completeRollsBackWhenOverRefunded() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(1, APPLICANT_ID, "36.00"));
        when(refundItemMapper.selectList(any()))
                .thenReturn(List.of(refundItem(4501L, 4101L, "阿莫西林胶囊", 2, "36.00")));
        when(chargeItemMapper.addRefundNum(anyLong(), anyInt())).thenReturn(0);
        loginAs(AUDITOR_ID, "finance");

        assertThatThrownBy(() -> service.complete(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可退数量不足");

        verify(refundMapper, never()).complete(anyLong(), anyLong());
        verify(stockMapper, never()).increaseByBatch(anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("退的是非药品（检查费）时不碰库存")
    void nonDrugRefundSkipsStock() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(1, APPLICANT_ID, "30.00"));
        BizRefundItem examItem = refundItem(4501L, 4102L, "血常规", 1, "30.00");
        examItem.setItemType(3);
        when(refundItemMapper.selectList(any())).thenReturn(List.of(examItem));
        when(chargeItemMapper.addRefundNum(4102L, 1)).thenReturn(1);
        when(refundMapper.complete(REFUND_ID, AUDITOR_ID)).thenReturn(1);
        loginAs(AUDITOR_ID, "finance");

        service.complete(REFUND_ID);

        verify(chargeItemMapper).addRefundNum(4102L, 1);
        verify(stockMapper, never()).increaseByBatch(anyLong(), anyLong(), anyString(), anyInt());
        verify(dispenseMapper, never()).returnBack(anyLong(), anyLong());
    }

    @Test
    @DisplayName("批次不存在时拒绝退款，不能让钱退了药回不去")
    void missingBatchRejectsRefund() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(refund(1, APPLICANT_ID, "36.00"));
        when(refundItemMapper.selectList(any()))
                .thenReturn(List.of(refundItem(4501L, 4101L, "阿莫西林胶囊", 2, "36.00")));
        when(chargeItemMapper.addRefundNum(4101L, 2)).thenReturn(1);
        when(chargeItemMapper.selectById(4101L)).thenReturn(chargeItem(4101L, "阿莫西林胶囊", 2, "18.00", 0));
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, "36.00"));
        BizDispense dispense = new BizDispense();
        dispense.setId(3001L);
        dispense.setWarehouseId(9201L);
        when(dispenseMapper.selectDoneByPresc(5001L)).thenReturn(dispense);
        BizDispenseItem dispensed = new BizDispenseItem();
        dispensed.setDrugId(9101L);
        dispensed.setBatchNo("BATCH001");
        when(dispenseItemMapper.selectList(any())).thenReturn(List.of(dispensed));
        when(stockMapper.increaseByBatch(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(0);
        loginAs(AUDITOR_ID, "finance");

        assertThatThrownBy(() -> service.complete(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批次");

        verify(refundMapper, never()).complete(anyLong(), anyLong());
    }

    /* ---------------- 查询 ---------------- */

    @Test
    @DisplayName("退费单不存在时报 40400")
    void refundNotFound() {
        when(refundMapper.selectById(REFUND_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退费单不存在");
    }
}
