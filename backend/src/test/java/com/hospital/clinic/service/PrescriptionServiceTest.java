package com.hospital.clinic.service;

import com.hospital.clinic.dto.PrescriptionItemRequest;
import com.hospital.clinic.dto.PrescriptionRequest;
import com.hospital.clinic.entity.BizPrescription;
import com.hospital.clinic.entity.BizPrescriptionItem;
import com.hospital.clinic.mapper.BizPrescriptionItemMapper;
import com.hospital.clinic.mapper.BizPrescriptionMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门诊处方：开方金额、药品校验、审核制衡、状态流转。
 *
 * <p>钱和药都在这里，任何一条放松都是真实损失：
 * 金额必须服务端算、停用药不能开、自己开的方不能自己审。</p>
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    private static final Long VISIT_ID = 500L;
    private static final Long PATIENT_ID = 100L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long DRUG_A = 9101L;
    private static final Long DRUG_B = 9102L;

    @Mock
    private BizPrescriptionMapper prescriptionMapper;
    @Mock
    private BizPrescriptionItemMapper itemMapper;
    @Mock
    private BizRegistrationMapper registrationMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private BizDrugMapper drugMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PrescNoGenerator noGenerator;

    private PrescriptionService service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionService(prescriptionMapper, itemMapper, registrationMapper,
                patientMapper, drugMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        // 处方主表 insert 后要能拿到 id（真实环境由雪花算法生成）。
        // 用 lenient：校验失败的用例根本走不到 insert，严格模式下会判为多余 stub
        lenient().doAnswer(inv -> {
            BizPrescription entity = inv.getArgument(0);
            entity.setId(9001L);
            return 1;
        }).when(prescriptionMapper).insert(any(BizPrescription.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("tester")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizRegistration visit(int status) {
        BizRegistration registration = new BizRegistration();
        registration.setId(VISIT_ID);
        registration.setPatientId(PATIENT_ID);
        registration.setDoctorId(DOCTOR_ID);
        registration.setStatus(status);
        registration.setRegNo("REG202610260001");
        return registration;
    }

    private BizDrug drug(Long id, String name, String price, int status) {
        BizDrug drug = new BizDrug();
        drug.setId(id);
        drug.setDrugName(name);
        drug.setSpec("10片/盒");
        drug.setSalePrice(new BigDecimal(price));
        drug.setStatus(status);
        return drug;
    }

    private PrescriptionItemRequest item(Long drugId, int quantity) {
        PrescriptionItemRequest item = new PrescriptionItemRequest();
        item.setDrugId(drugId);
        item.setQuantity(quantity);
        item.setUsage("口服");
        item.setDosage("1片");
        item.setFrequency("每日3次");
        item.setDays(3);
        return item;
    }

    private PrescriptionRequest request(PrescriptionItemRequest... items) {
        PrescriptionRequest request = new PrescriptionRequest();
        request.setVisitId(VISIT_ID);
        request.setPrescType(1);
        request.setDiagnosis("上呼吸道感染");
        request.setItems(List.of(items));
        return request;
    }

    @Test
    @DisplayName("未接诊不能开方（只有就诊中的挂号才能开）")
    void prescribingBeforeReceiveIsRejected() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(visit(0));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.create(request(item(DRUG_A, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先接诊");

        verify(prescriptionMapper, never()).insert(any(BizPrescription.class));
    }

    @Test
    @DisplayName("已退号 / 已完成的就诊不能开方")
    void prescribingForClosedVisitIsRejected() {
        loginAs(DOCTOR_ID, "doctor");

        when(registrationMapper.selectById(VISIT_ID)).thenReturn(visit(3));
        assertThatThrownBy(() -> service.create(request(item(DRUG_A, 1))))
                .hasMessageContaining("已退号");

        when(registrationMapper.selectById(VISIT_ID)).thenReturn(visit(2));
        assertThatThrownBy(() -> service.create(request(item(DRUG_A, 1))))
                .hasMessageContaining("已完成");
    }

    @Test
    @DisplayName("金额由服务端按药品售价计算，前端无从干预")
    void amountIsComputedOnServer() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(visit(1));
        when(drugMapper.selectByIds(any())).thenReturn(List.of(
                drug(DRUG_A, "氯化钠注射液", "4.00", 1),
                drug(DRUG_B, "头孢克肟分散片", "32.00", 1)));
        when(noGenerator.next()).thenReturn("PRESC202610260001");
        loginAs(DOCTOR_ID, "doctor");

        service.create(request(item(DRUG_A, 3), item(DRUG_B, 2)));

        ArgumentCaptor<BizPrescription> prescCaptor = ArgumentCaptor.forClass(BizPrescription.class);
        verify(prescriptionMapper).insert(prescCaptor.capture());
        BizPrescription saved = prescCaptor.getValue();

        // 4.00 × 3 + 32.00 × 2 = 76.00
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("76.00"));
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getPrescNo()).isEqualTo("PRESC202610260001");
        // 医生与患者取自挂号记录，不采用前端传值
        assertThat(saved.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(saved.getPatientId()).isEqualTo(PATIENT_ID);

        ArgumentCaptor<BizPrescriptionItem> itemCaptor = ArgumentCaptor.forClass(BizPrescriptionItem.class);
        verify(itemMapper, times(2)).insert(itemCaptor.capture());
        List<BizPrescriptionItem> items = itemCaptor.getAllValues();
        assertThat(items.get(0).getDrugName()).isEqualTo("氯化钠注射液");
        assertThat(items.get(0).getSpec()).isEqualTo("10片/盒");
        assertThat(items.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(items.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(items.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("64.00"));
    }

    @Test
    @DisplayName("药品不存在或已停用时拒绝开方")
    void invalidDrugIsRejected() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(visit(1));
        loginAs(DOCTOR_ID, "doctor");

        when(drugMapper.selectByIds(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(request(item(DRUG_A, 1))))
                .hasMessageContaining("药品不存在");

        when(drugMapper.selectByIds(any())).thenReturn(List.of(
                drug(DRUG_A, "停用药", "4.00", 0)));
        assertThatThrownBy(() -> service.create(request(item(DRUG_A, 1))))
                .hasMessageContaining("已停用");

        verify(prescriptionMapper, never()).insert(any(BizPrescription.class));
    }

    @Test
    @DisplayName("医生不能审核自己开的处方（否则审核环节形同虚设）")
    void doctorCannotAuditOwnPrescription() {
        BizPrescription prescription = new BizPrescription();
        prescription.setId(9001L);
        prescription.setDoctorId(DOCTOR_ID);
        prescription.setStatus(0);
        when(prescriptionMapper.selectById(9001L)).thenReturn(prescription);
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.audit(9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能审核自己");

        verify(prescriptionMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("管理员可审核（系统管理员默认承担全部职责，生产应拆独立药师）")
    void adminCanAudit() {
        BizPrescription prescription = new BizPrescription();
        prescription.setId(9001L);
        prescription.setDoctorId(1L);
        prescription.setStatus(0);
        when(prescriptionMapper.selectById(9001L)).thenReturn(prescription);
        when(prescriptionMapper.transitStatus(9001L, 0, 1, 1L)).thenReturn(1);
        loginAs(1L, "admin");

        service.audit(9001L);

        verify(prescriptionMapper).transitStatus(9001L, 0, 1, 1L);
    }

    @Test
    @DisplayName("审核时状态已变（被别人先审了）返回冲突")
    void auditConflictWhenStatusAlreadyChanged() {
        BizPrescription prescription = new BizPrescription();
        prescription.setId(9001L);
        prescription.setDoctorId(3L);
        prescription.setStatus(0);
        when(prescriptionMapper.selectById(9001L)).thenReturn(prescription);
        when(prescriptionMapper.transitStatus(eq(9001L), any(), any(), any())).thenReturn(0);
        loginAs(1L, "admin");

        assertThatThrownBy(() -> service.audit(9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("已收费 / 已发药的处方不能直接作废，必须走退费")
    void cancelChargedOrDispensedIsRejected() {
        BizPrescription charged = new BizPrescription();
        charged.setId(9001L);
        charged.setStatus(2);
        when(prescriptionMapper.selectById(9001L)).thenReturn(charged);
        loginAs(1L, "admin");

        assertThatThrownBy(() -> service.cancel(9001L))
                .hasMessageContaining("退费流程");

        BizPrescription dispensed = new BizPrescription();
        dispensed.setId(9002L);
        dispensed.setStatus(3);
        when(prescriptionMapper.selectById(9002L)).thenReturn(dispensed);

        assertThatThrownBy(() -> service.cancel(9002L))
                .hasMessageContaining("退费流程");
    }

    @Test
    @DisplayName("待审核处方可以作废，且用条件更新防并发")
    void cancelPendingPrescription() {
        BizPrescription prescription = new BizPrescription();
        prescription.setId(9001L);
        prescription.setStatus(0);
        when(prescriptionMapper.selectById(9001L)).thenReturn(prescription);
        when(prescriptionMapper.transitStatus(eq(9001L), eq(0), eq(4), any())).thenReturn(1);
        loginAs(1L, "admin");

        service.cancel(9001L);

        verify(prescriptionMapper).transitStatus(9001L, 0, 4, 1L);
    }

    @Test
    @DisplayName("重复作废返回明确提示，不会静默成功")
    void cancelTwiceIsRejected() {
        BizPrescription prescription = new BizPrescription();
        prescription.setId(9001L);
        prescription.setStatus(4);
        when(prescriptionMapper.selectById(9001L)).thenReturn(prescription);
        loginAs(1L, "admin");

        assertThatThrownBy(() -> service.cancel(9001L))
                .hasMessageContaining("已作废");
    }
}
