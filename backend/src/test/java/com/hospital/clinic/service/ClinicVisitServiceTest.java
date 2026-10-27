package com.hospital.clinic.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.clinic.dto.ReceiveResult;
import com.hospital.clinic.entity.BizMedicalRecord;
import com.hospital.clinic.mapper.BizMedicalRecordMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接诊：数据权限、状态门禁、幂等。
 *
 * <p>接诊是「把别人的号接走」最直接的方式 —— 只校验登录而不校验归属，
 * 改一下 URL 里的 id 就能看别人的患者。所以数据权限这条必须钉死。</p>
 */
@ExtendWith(MockitoExtension.class)
class ClinicVisitServiceTest {

    private static final Long VISIT_ID = 9001L;
    private static final Long PATIENT_ID = 100L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long OTHER_DOCTOR_ID = 4L;

    @Mock
    private BizRegistrationMapper registrationMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private BizMedicalRecordMapper recordMapper;

    private ClinicVisitService service;

    /**
     * 纯单测没有 MyBatis 上下文，Service 里的 {@code Wrappers.lambdaUpdate()}
     * 会因缺少 TableInfo 缓存抛 {@code can not find lambda cache}，这里手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), BizRegistration.class);
    }

    @BeforeEach
    void setUp() {
        service = new ClinicVisitService(registrationMapper, patientMapper, recordMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("doctor")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizRegistration registration(int status, Long doctorId) {
        BizRegistration entity = new BizRegistration();
        entity.setId(VISIT_ID);
        entity.setPatientId(PATIENT_ID);
        entity.setDoctorId(doctorId);
        entity.setStatus(status);
        entity.setRegNo("REG202610260001");
        entity.setQueueNo(3);
        return entity;
    }

    private BizPatient patient() {
        BizPatient entity = new BizPatient();
        entity.setId(PATIENT_ID);
        entity.setPatientNo("P0001");
        entity.setName("张三");
        entity.setGender(1);
        entity.setBirthDate(LocalDate.now().minusYears(35));
        entity.setAllergyHistory("青霉素过敏");
        return entity;
    }

    @Test
    @DisplayName("挂号不存在时返回 40400")
    void visitNotFound() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.receive(VISIT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("已退号 / 已完成的挂号不能接诊")
    void closedVisitIsRejected() {
        loginAs(DOCTOR_ID, "doctor");

        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(3, DOCTOR_ID));
        assertThatThrownBy(() -> service.receive(VISIT_ID)).hasMessageContaining("已退号");

        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(2, DOCTOR_ID));
        assertThatThrownBy(() -> service.receive(VISIT_ID)).hasMessageContaining("已完成");
    }

    @Test
    @DisplayName("医生接诊别人的号被拒（40302 数据权限，不是 40301 功能权限）")
    void doctorCannotReceiveOthersVisit() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(0, OTHER_DOCTOR_ID));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.receive(VISIT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能接诊挂在自己名下");

        verify(registrationMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("管理员可以接诊任意医生的号（用于前台代接诊）")
    void adminCanReceiveAnyVisit() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(0, OTHER_DOCTOR_ID));
        when(registrationMapper.update(any(), any())).thenReturn(1);
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(1L, "admin");

        ReceiveResult result = service.receive(VISIT_ID);

        assertThat(result.getVisitStatus()).isEqualTo(1);
        verify(registrationMapper).update(any(), any());
    }

    @Test
    @DisplayName("接诊成功：返回患者信息与过敏史，供开方前核对")
    void receiveReturnsPatientContext() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(0, DOCTOR_ID));
        when(registrationMapper.update(any(), any())).thenReturn(1);
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        ReceiveResult result = service.receive(VISIT_ID);

        assertThat(result.getRegNo()).isEqualTo("REG202610260001");
        assertThat(result.getQueueNo()).isEqualTo(3);
        assertThat(result.getPatientName()).isEqualTo("张三");
        assertThat(result.getAge()).isEqualTo(35);
        // 过敏史必须带回，开方前最该看的就是这项
        assertThat(result.getAllergyHistory()).isEqualTo("青霉素过敏");
        assertThat(result.getVisitStatus()).isEqualTo(1);
        assertThat(result.getRecordId()).isNull();
    }

    @Test
    @DisplayName("重复接诊是幂等的：不再改状态，直接返回工作台数据")
    void receiveIsIdempotent() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        ReceiveResult result = service.receive(VISIT_ID);

        assertThat(result.getVisitStatus()).isEqualTo(1);
        // 已经是就诊中，不应该重复更新
        verify(registrationMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("已有病历时返回病历 id 与状态，前端据此进入编辑或只读")
    void receiveReturnsExistingRecord() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        BizMedicalRecord record = new BizMedicalRecord();
        record.setId(7001L);
        record.setStatus(1);
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(record);
        loginAs(DOCTOR_ID, "doctor");

        ReceiveResult result = service.receive(VISIT_ID);

        assertThat(result.getRecordId()).isEqualTo("7001");
        assertThat(result.getRecordStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("患者档案丢失时不返回残缺数据")
    void missingPatientIsReported() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.receive(VISIT_ID))
                .hasMessageContaining("患者档案不存在");
    }

    @Test
    @DisplayName("并发接诊时另一个请求已改状态也能正确返回，不报错")
    void concurrentReceiveFallsBackToLatestStatus() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(0, DOCTOR_ID));
        // 条件更新影响 0 行 = 别人抢先接诊了
        when(registrationMapper.update(any(), any())).thenReturn(0);
        BizRegistration latest = registration(1, DOCTOR_ID);
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(0, DOCTOR_ID), latest);
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        ReceiveResult result = service.receive(VISIT_ID);

        assertThat(result.getVisitStatus()).isEqualTo(1);
    }
}
