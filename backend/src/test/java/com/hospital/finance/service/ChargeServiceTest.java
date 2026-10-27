package com.hospital.finance.service;

import com.hospital.clinic.entity.BizPrescription;
import com.hospital.clinic.entity.BizPrescriptionItem;
import com.hospital.clinic.mapper.BizPrescriptionItemMapper;
import com.hospital.clinic.mapper.BizPrescriptionMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.exam.entity.BizExamApply;
import com.hospital.exam.entity.BizExamItem;
import com.hospital.finance.dto.ChargeRequest;
import com.hospital.finance.dto.ExamChargeRequest;
import com.hospital.finance.dto.ChargeResult;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizChargeItem;
import com.hospital.finance.entity.BizTreatItem;
import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamItemMapper;
import com.hospital.finance.mapper.BizChargeItemMapper;
import com.hospital.finance.mapper.BizTreatItemMapper;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 处方收费：金额自洽、状态门禁、防重复收费。
 *
 * <p>钱的问题必须逐分核对，所以这里既断言金额数值，也断言「不一致时必须报错」。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    private static final Long PRESC_ID = 9001L;
    private static final Long PATIENT_ID = 100L;
    private static final Long VISIT_ID = 500L;
    private static final Long OPERATOR_ID = 1L;

    @Mock
    private BizChargeMapper chargeMapper;
    @Mock
    private BizChargeItemMapper chargeItemMapper;
    @Mock
    private BizPrescriptionMapper prescriptionMapper;
    @Mock
    private BizPrescriptionItemMapper prescriptionItemMapper;
    @Mock
    private BizTreatItemMapper treatItemMapper;
    @Mock
    private BizExamApplyMapper examApplyMapper;
    @Mock
    private BizExamItemMapper examItemMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ChargeNoGenerator noGenerator;

    private ChargeService service;

    @BeforeEach
    void setUp() {
        service = new ChargeService(chargeMapper, chargeItemMapper, prescriptionMapper,
                prescriptionItemMapper, treatItemMapper, examApplyMapper, examItemMapper,
                patientMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        // lenient：参数校验失败的用例走不到 insert，严格模式下会被判为多余 stub
        lenient().doAnswer(inv -> {
            BizCharge entity = inv.getArgument(0);
            entity.setId(7001L);
            return 1;
        }).when(chargeMapper).insert(any(BizCharge.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("cashier")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPrescription prescription(int status, String total) {
        BizPrescription entity = new BizPrescription();
        entity.setId(PRESC_ID);
        entity.setPrescNo("PRESC202610260001");
        entity.setPatientId(PATIENT_ID);
        entity.setVisitId(VISIT_ID);
        entity.setDoctorId(3L);
        entity.setStatus(status);
        entity.setTotalAmount(new BigDecimal(total));
        return entity;
    }

    private BizPrescriptionItem item(String name, String price, int quantity) {
        BizPrescriptionItem entity = new BizPrescriptionItem();
        entity.setDrugId(9101L);
        entity.setDrugName(name);
        entity.setPrice(new BigDecimal(price));
        entity.setQuantity(quantity);
        entity.setAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        return entity;
    }

    private ChargeRequest request(String discount) {
        ChargeRequest request = new ChargeRequest();
        request.setPrescId(PRESC_ID);
        request.setPayType(2);
        if (discount != null) {
            request.setDiscountAmount(new BigDecimal(discount));
        }
        return request;
    }

    @Test
    @DisplayName("只有已审核的处方才能收费")
    void onlyAuditedPrescriptionCanBeCharged() {
        loginAs(OPERATOR_ID, "cashier");

        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(0, "76.00"));
        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .hasMessageContaining("尚未审核");

        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2, "76.00"));
        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .hasMessageContaining("已收费");

        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(4, "76.00"));
        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .hasMessageContaining("已作废");

        verify(chargeMapper, never()).insert(any(BizCharge.class));
    }

    @Test
    @DisplayName("处方无明细时拒绝收费（没有明细就无从核对金额）")
    void emptyPrescriptionIsRejected() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1, "0.00"));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of());
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .hasMessageContaining("没有药品明细");
    }

    @Test
    @DisplayName("处方总额与明细汇总不一致时拒绝收费（防数据被改）")
    void amountMismatchIsRejected() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1, "80.00"));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(
                item("氯化钠注射液", "4.00", 3),
                item("头孢克肟分散片", "32.00", 2)));
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");

        verify(chargeMapper, never()).insert(any(BizCharge.class));
    }

    @Test
    @DisplayName("优惠金额不能超过应收金额")
    void discountCannotExceedTotal() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1, "76.00"));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(
                item("氯化钠注射液", "4.00", 3),
                item("头孢克肟分散片", "32.00", 2)));
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargePrescription(request("100.00")))
                .hasMessageContaining("优惠金额不能超过");
    }

    @Test
    @DisplayName("正常收费：三方金额自洽，明细快照落库，处方推进到已收费")
    void chargeSucceedsWithConsistentAmounts() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1, "76.00"));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(
                item("氯化钠注射液", "4.00", 3),
                item("头孢克肟分散片", "32.00", 2)));
        when(noGenerator.next()).thenReturn("CHG202610260001");
        when(prescriptionMapper.transitStatus(PRESC_ID, 1, 2, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "cashier");

        ChargeResult result = service.chargePrescription(request("6.00"));

        assertThat(result.getChargeNo()).isEqualTo("CHG202610260001");
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("76.00"));
        assertThat(result.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(result.getActualAmount()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(result.getPayStatus()).isEqualTo(1);

        ArgumentCaptor<BizCharge> captor = ArgumentCaptor.forClass(BizCharge.class);
        verify(chargeMapper).insert(captor.capture());
        BizCharge saved = captor.getValue();
        assertThat(saved.getSourceType()).isEqualTo(1);
        assertThat(saved.getSourceId()).isEqualTo(PRESC_ID);
        assertThat(saved.getChargeType()).isEqualTo(2);
        assertThat(saved.getOperatorId()).isEqualTo(OPERATOR_ID);

        verify(chargeItemMapper, times(2)).insert(any(BizChargeItem.class));
        verify(prescriptionMapper).transitStatus(PRESC_ID, 1, 2, OPERATOR_ID);
    }

    @Test
    @DisplayName("并发/重复收费撞唯一索引时返回冲突而不是 500")
    void duplicateChargeIsReportedAsConflict() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1, "76.00"));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(
                item("氯化钠注射液", "4.00", 3),
                item("头孢克肟分散片", "32.00", 2)));
        when(noGenerator.next()).thenReturn("CHG202610260002");
        doThrow(new DuplicateKeyException("uk_charge_source"))
                .when(chargeMapper).insert(any(BizCharge.class));
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收费");
    }

    @Test
    @DisplayName("处方状态流转失败（被别人抢先）时整单回滚并返回冲突")
    void prescriptionStatusConflictRollsBack() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1, "76.00"));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(
                item("氯化钠注射液", "4.00", 3),
                item("头孢克肟分散片", "32.00", 2)));
        when(noGenerator.next()).thenReturn("CHG202610260003");
        when(prescriptionMapper.transitStatus(eq(PRESC_ID), any(), any(), any())).thenReturn(0);
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargePrescription(request(null)))
                .hasMessageContaining("状态已变化");
    }

    /* ---------------- 检查检验收费 ---------------- */

    private BizExamApply examApply(Integer status) {
        BizExamApply apply = new BizExamApply();
        apply.setId(8801L);
        apply.setApplyNo("EXM202610260001");
        apply.setPatientId(PATIENT_ID);
        apply.setVisitId(VISIT_ID);
        apply.setStatus(status);
        return apply;
    }

    private BizExamItem examItem(Long id, String code, String name) {
        BizExamItem item = new BizExamItem();
        item.setId(id);
        item.setApplyId(8801L);
        item.setItemCode(code);
        item.setItemName(name);
        return item;
    }

    private BizTreatItem price(Long id, String name, String amount) {
        BizTreatItem treatItem = new BizTreatItem();
        treatItem.setId(id);
        treatItem.setItemName(name);
        treatItem.setPrice(new BigDecimal(amount));
        return treatItem;
    }

    private ExamChargeRequest examRequest(BigDecimal discount) {
        ExamChargeRequest request = new ExamChargeRequest();
        request.setApplyId(8801L);
        request.setPayType(1);
        request.setDiscountAmount(discount);
        return request;
    }

    @Test
    @DisplayName("检查收费：金额按价目表算，收完推进到待采样")
    void chargeExamUsesPriceCatalog() {
        when(examApplyMapper.selectById(8801L)).thenReturn(examApply(0));
        when(examItemMapper.selectList(any())).thenReturn(List.of(
                examItem(1L, "EXM_CBC", "血常规"),
                examItem(2L, "EXM_CXR", "胸部X光正位")));
        when(treatItemMapper.selectEffective("EXM_CBC")).thenReturn(price(9010L, "血常规", "30.00"));
        when(treatItemMapper.selectEffective("EXM_CXR")).thenReturn(price(9020L, "胸部X光正位", "60.00"));
        when(noGenerator.next()).thenReturn("CHG202610260010");
        when(examApplyMapper.transitStatus(eq(8801L), eq(0), eq(1), any())).thenReturn(1);
        loginAs(OPERATOR_ID, "cashier");

        ChargeResult result = service.chargeExam(examRequest(BigDecimal.ZERO));

        // 30 + 60 = 90，金额来自价目表而不是请求体
        assertThat(result.getTotalAmount()).isEqualByComparingTo("90.00");
        assertThat(result.getActualAmount()).isEqualByComparingTo("90.00");

        ArgumentCaptor<BizCharge> captor = ArgumentCaptor.forClass(BizCharge.class);
        verify(chargeMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo(2);
        assertThat(captor.getValue().getSourceId()).isEqualTo(8801L);

        ArgumentCaptor<BizChargeItem> itemCaptor = ArgumentCaptor.forClass(BizChargeItem.class);
        verify(chargeItemMapper, times(2)).insert(itemCaptor.capture());
        // item_type=3 是「检查」，逐条落明细便于退费时按项退
        assertThat(itemCaptor.getAllValues()).allSatisfy(i -> assertThat(i.getItemType()).isEqualTo(3));

        verify(examApplyMapper).transitStatus(8801L, 0, 1, OPERATOR_ID);
    }

    @Test
    @DisplayName("已缴费的检查单不能重复收费（40902）")
    void chargeExamRejectsPaidApply() {
        when(examApplyMapper.selectById(8801L)).thenReturn(examApply(1));
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargeExam(examRequest(BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已缴费");

        verify(chargeMapper, never()).insert(any(BizCharge.class));
    }

    @Test
    @DisplayName("检查项目未维护价格时拒绝收费，不按 0 元放行")
    void chargeExamRejectsUnpricedItem() {
        when(examApplyMapper.selectById(8801L)).thenReturn(examApply(0));
        when(examItemMapper.selectList(any())).thenReturn(List.of(examItem(1L, "EXM_NEW", "新项目")));
        when(treatItemMapper.selectEffective("EXM_NEW")).thenReturn(null);
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargeExam(examRequest(BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未维护价格");

        verify(chargeMapper, never()).insert(any(BizCharge.class));
    }

    @Test
    @DisplayName("检查单不存在时报 40400")
    void chargeExamRejectsMissingApply() {
        when(examApplyMapper.selectById(8801L)).thenReturn(null);
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargeExam(examRequest(BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查单不存在");
    }

    @Test
    @DisplayName("并发重复收费撞唯一键时转成冲突提示")
    void chargeExamDuplicateKeyBecomesConflict() {
        when(examApplyMapper.selectById(8801L)).thenReturn(examApply(0));
        when(examItemMapper.selectList(any())).thenReturn(List.of(examItem(1L, "EXM_CBC", "血常规")));
        when(treatItemMapper.selectEffective("EXM_CBC")).thenReturn(price(9010L, "血常规", "30.00"));
        when(noGenerator.next()).thenReturn("CHG202610260011");
        doThrow(new DuplicateKeyException("uk_charge_source")).when(chargeMapper).insert(any(BizCharge.class));
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargeExam(examRequest(BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收费");

        verify(examApplyMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("检查单状态被并发改掉时整单回滚")
    void chargeExamStatusConflictRollsBack() {
        when(examApplyMapper.selectById(8801L)).thenReturn(examApply(0));
        when(examItemMapper.selectList(any())).thenReturn(List.of(examItem(1L, "EXM_CBC", "血常规")));
        when(treatItemMapper.selectEffective("EXM_CBC")).thenReturn(price(9010L, "血常规", "30.00"));
        when(noGenerator.next()).thenReturn("CHG202610260012");
        when(examApplyMapper.transitStatus(anyLong(), any(), any(), any())).thenReturn(0);
        loginAs(OPERATOR_ID, "cashier");

        assertThatThrownBy(() -> service.chargeExam(examRequest(BigDecimal.ZERO)))
                .hasMessageContaining("状态已变化");
    }
}
