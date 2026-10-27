package com.hospital.clinic.service;

import com.hospital.clinic.dto.MedicalRecordRequest;
import com.hospital.clinic.dto.MedicalRecordVO;
import com.hospital.clinic.entity.BizMedicalRecord;
import com.hospital.clinic.mapper.BizMedicalRecordMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.system.entity.SysUser;
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

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门诊病历：必填校验、提交后锁定、一挂号一份。
 *
 * <p>病历是要承担法律责任的文书，所以「提交后不可改」和「提交必须有主诉与诊断」
 * 这两条不能有任何松动。</p>
 */
@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    private static final Long VISIT_ID = 9001L;
    private static final Long PATIENT_ID = 100L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long OTHER_DOCTOR_ID = 4L;
    private static final Long RECORD_ID = 7001L;

    @Mock
    private BizMedicalRecordMapper recordMapper;
    @Mock
    private BizRegistrationMapper registrationMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;

    private MedicalRecordService service;

    @BeforeEach
    void setUp() {
        service = new MedicalRecordService(recordMapper, registrationMapper, patientMapper, userMapper);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizMedicalRecord entity = inv.getArgument(0);
            entity.setId(RECORD_ID);
            return 1;
        }).when(recordMapper).insert(any(BizMedicalRecord.class));
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
        return entity;
    }

    private MedicalRecordRequest request(boolean submit) {
        MedicalRecordRequest request = new MedicalRecordRequest();
        request.setVisitId(VISIT_ID);
        request.setChiefComplaint("发热咳嗽3天");
        request.setPresentIllness("3天前受凉后发热");
        request.setPhysicalExam("咽充血");
        request.setDiagnosis("急性上呼吸道感染");
        request.setIcdCode("J06.9");
        request.setSubmit(submit);
        return request;
    }

    @Test
    @DisplayName("挂号不存在时返回 40400")
    void visitNotFound() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.save(request(false)))
                .hasMessageContaining("挂号记录不存在");
    }

    @Test
    @DisplayName("已退号的挂号不能书写病历")
    void cancelledVisitIsRejected() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(3, DOCTOR_ID));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.save(request(false)))
                .hasMessageContaining("已退号");
    }

    @Test
    @DisplayName("医生只能书写自己接诊患者的病历（40302）")
    void cannotWriteOthersRecord() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, OTHER_DOCTOR_ID));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.save(request(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能书写自己接诊");

        verify(recordMapper, never()).insert(any(BizMedicalRecord.class));
    }

    @Test
    @DisplayName("提交时主诉与诊断必填")
    void submitRequiresChiefComplaintAndDiagnosis() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        MedicalRecordRequest noChief = request(true);
        noChief.setChiefComplaint(null);
        assertThatThrownBy(() -> service.save(noChief)).hasMessageContaining("主诉不能为空");

        MedicalRecordRequest noDiagnosis = request(true);
        noDiagnosis.setDiagnosis("  ");
        assertThatThrownBy(() -> service.save(noDiagnosis)).hasMessageContaining("诊断不能为空");
    }

    @Test
    @DisplayName("存草稿不校验必填，允许中途离开再回来继续写")
    void draftAllowsMissingFields() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        MedicalRecordRequest draft = new MedicalRecordRequest();
        draft.setVisitId(VISIT_ID);
        draft.setSubmit(false);

        Long id = service.save(draft);

        assertThat(id).isEqualTo(RECORD_ID);
        ArgumentCaptor<BizMedicalRecord> captor = ArgumentCaptor.forClass(BizMedicalRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    @DisplayName("首次保存落库：visitNo 复用挂号单号，患者与医生取自挂号")
    void firstSaveBuildsRecordFromRegistration() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        service.save(request(true));

        ArgumentCaptor<BizMedicalRecord> captor = ArgumentCaptor.forClass(BizMedicalRecord.class);
        verify(recordMapper).insert(captor.capture());
        BizMedicalRecord saved = captor.getValue();

        assertThat(saved.getVisitId()).isEqualTo(VISIT_ID);
        // 就诊流水号直接复用挂号单号，人工核对两个号能对上
        assertThat(saved.getVisitNo()).isEqualTo("REG202610260001");
        assertThat(saved.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(saved.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(saved.getVisitType()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("同一就诊再次保存时覆盖原草稿，不会产生第二份病历")
    void secondSaveUpdatesSameRecord() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        BizMedicalRecord existing = new BizMedicalRecord();
        existing.setId(RECORD_ID);
        existing.setStatus(0);
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(existing);
        loginAs(DOCTOR_ID, "doctor");

        Long id = service.save(request(true));

        assertThat(id).isEqualTo(RECORD_ID);
        verify(recordMapper).updateById(any(BizMedicalRecord.class));
        verify(recordMapper, never()).insert(any(BizMedicalRecord.class));
    }

    @Test
    @DisplayName("已提交的病历不能再改，且该判断优先于内容校验")
    void submittedRecordIsLocked() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        BizMedicalRecord submitted = new BizMedicalRecord();
        submitted.setId(RECORD_ID);
        submitted.setStatus(1);
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(submitted);
        loginAs(DOCTOR_ID, "doctor");

        // 即使请求内容不合法，也应先报「已提交不能改」，
        // 否则用户会以为补上字段就能改，白折腾一轮
        MedicalRecordRequest incomplete = new MedicalRecordRequest();
        incomplete.setVisitId(VISIT_ID);
        incomplete.setSubmit(true);

        assertThatThrownBy(() -> service.save(incomplete))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交");

        verify(recordMapper, never()).updateById(any(BizMedicalRecord.class));
    }

    @Test
    @DisplayName("尚未书写的就诊查询病历返回 null，而不是抛异常")
    void getMissingRecordReturnsNull() {
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(null);

        assertThat(service.getByVisit(VISIT_ID)).isNull();
    }

    @Test
    @DisplayName("查询病历时补全患者与医生姓名")
    void getRecordFillsNames() {
        BizMedicalRecord record = new BizMedicalRecord();
        record.setId(RECORD_ID);
        record.setVisitId(VISIT_ID);
        record.setVisitNo("REG202610260001");
        record.setPatientId(PATIENT_ID);
        record.setDoctorId(DOCTOR_ID);
        record.setStatus(1);
        record.setDiagnosis("急性上呼吸道感染");
        when(recordMapper.selectByVisitId(VISIT_ID)).thenReturn(record);

        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("张三");
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient);

        SysUser doctor = new SysUser();
        doctor.setId(DOCTOR_ID);
        doctor.setRealName("张医生");
        when(userMapper.selectById(DOCTOR_ID)).thenReturn(doctor);

        MedicalRecordVO vo = service.getByVisit(VISIT_ID);

        assertThat(vo.getPatientName()).isEqualTo("张三");
        assertThat(vo.getDoctorName()).isEqualTo("张医生");
        assertThat(vo.getDiagnosis()).isEqualTo("急性上呼吸道感染");
        assertThat(vo.getVisitId()).isEqualTo("9001");
    }
}
