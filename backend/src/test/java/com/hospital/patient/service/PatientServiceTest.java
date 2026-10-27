package com.hospital.patient.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.util.HashUtil;
import com.hospital.patient.dto.PatientQuery;
import com.hospital.patient.dto.PatientSaveRequest;
import com.hospital.patient.dto.PatientVO;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 患者建档：身份证查重、删除保护、敏感字段脱敏。
 *
 * <p>这几条一旦松动，后果是「同一个人建了三个档案」或「患者隐私明文外泄」，
 * 都是后期极难清理的数据事故。</p>
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    private static final Long PATIENT_ID = 100L;

    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private PatientNoGenerator noGenerator;

    private PatientService service;

    @BeforeEach
    void setUp() {
        service = new PatientService(patientMapper, noGenerator);
    }

    private PatientSaveRequest request(String name, String idCard, String phone) {
        PatientSaveRequest request = new PatientSaveRequest();
        request.setName(name);
        request.setGender(1);
        request.setBirthDate(LocalDate.of(1990, 5, 1));
        request.setIdCard(idCard);
        request.setPhone(phone);
        request.setBloodType("A");
        request.setAllergyHistory("青霉素过敏");
        request.setMedicalHistory("高血压 5 年");
        return request;
    }

    private BizPatient entity() {
        BizPatient entity = new BizPatient();
        entity.setId(PATIENT_ID);
        entity.setPatientNo("P0001");
        entity.setName("张三");
        entity.setGender(1);
        entity.setBirthDate(LocalDate.of(1990, 5, 1));
        entity.setIdCard("440300199005011234");
        entity.setPhone("13800001111");
        entity.setBloodType("A");
        entity.setAllergyHistory("青霉素过敏");
        entity.setMedicalHistory("高血压 5 年");
        return entity;
    }

    @Test
    @DisplayName("建档时生成编号、落 hash、卡余额初始为 0")
    void createPatientFillsServerSideFields() {
        when(patientMapper.countByIdCardHash(any())).thenReturn(0);
        when(noGenerator.next()).thenReturn("P0001");

        service.create(request("张三", "440300199005011234", "13800001111"));

        ArgumentCaptor<BizPatient> captor = ArgumentCaptor.forClass(BizPatient.class);
        verify(patientMapper).insert(captor.capture());
        BizPatient saved = captor.getValue();

        assertThat(saved.getPatientNo()).isEqualTo("P0001");
        // 身份证 hash 用于查重，必须与 HashUtil 口径一致
        assertThat(saved.getIdCardHash()).isEqualTo(HashUtil.sha256Hex("440300199005011234"));
        assertThat(saved.getPhoneHash()).isEqualTo(HashUtil.sha256Hex("13800001111"));
        assertThat(saved.getCardBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("身份证重复时拒绝建档，提示明确而不是让数据库报 1062")
    void duplicateIdCardIsRejected() {
        when(patientMapper.countByIdCardHash(HashUtil.sha256Hex("440300199005011234"))).thenReturn(1);

        assertThatThrownBy(() -> service.create(request("张三", "440300199005011234", "13800001111")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已建档");

        verify(patientMapper, never()).insert(any(BizPatient.class));
    }

    @Test
    @DisplayName("修改时排除自己，改其他字段不会被自己的身份证挡住")
    void updateExcludesSelfWhenCheckingIdCard() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());
        when(patientMapper.countByIdCardHashExcluding(any(), eq(PATIENT_ID))).thenReturn(0);

        service.update(PATIENT_ID, request("张三改", "440300199005011234", "13800001111"));

        verify(patientMapper).updateById(any(BizPatient.class));
    }

    @Test
    @DisplayName("修改时身份证留空不清空原值（否则改个住址就把身份证连 hash 一起抹了）")
    void updateKeepsIdCardWhenBlank() {
        BizPatient existing = entity();
        existing.setIdCardHash(HashUtil.sha256Hex("440300199005011234"));
        existing.setPhoneHash(HashUtil.sha256Hex("13800001111"));
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(existing);

        PatientSaveRequest request = request("张三改", "", "");
        service.update(PATIENT_ID, request);

        ArgumentCaptor<BizPatient> captor = ArgumentCaptor.forClass(BizPatient.class);
        verify(patientMapper).updateById(captor.capture());
        BizPatient saved = captor.getValue();
        assertThat(saved.getIdCard()).isEqualTo("440300199005011234");
        assertThat(saved.getPhone()).isEqualTo("13800001111");
        assertThat(saved.getIdCardHash()).isEqualTo(existing.getIdCardHash());
        assertThat(saved.getPhoneHash()).isEqualTo(existing.getPhoneHash());
        // 姓名这类非敏感字段照常更新
        assertThat(saved.getName()).isEqualTo("张三改");
    }

    @Test
    @DisplayName("修改时回填脱敏值（带星号）同样不清空原值")
    void updateIgnoresMaskedValues() {
        BizPatient existing = entity();
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(existing);

        service.update(PATIENT_ID, request("张三改", "4403**********1234", "138****1111"));

        ArgumentCaptor<BizPatient> captor = ArgumentCaptor.forClass(BizPatient.class);
        verify(patientMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIdCard()).isEqualTo("440300199005011234");
        assertThat(captor.getValue().getPhone()).isEqualTo("13800001111");
    }

    @Test
    @DisplayName("修改时给了新的真实身份证则更新密文与 hash")
    void updateRefreshesHashWhenRealIdCardGiven() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());
        when(patientMapper.countByIdCardHashExcluding(any(), eq(PATIENT_ID))).thenReturn(0);

        service.update(PATIENT_ID, request("张三改", "440300199005016666", "13800002222"));

        ArgumentCaptor<BizPatient> captor = ArgumentCaptor.forClass(BizPatient.class);
        verify(patientMapper).updateById(captor.capture());
        BizPatient saved = captor.getValue();
        assertThat(saved.getIdCard()).isEqualTo("440300199005016666");
        assertThat(saved.getIdCardHash()).isEqualTo(HashUtil.sha256Hex("440300199005016666"));
        assertThat(saved.getPhoneHash()).isEqualTo(HashUtil.sha256Hex("13800002222"));
    }

    @Test
    @DisplayName("修改时撞到别人的身份证要拒绝")
    void updateConflictWithOtherPatientIsRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());
        when(patientMapper.countByIdCardHashExcluding(any(), eq(PATIENT_ID))).thenReturn(1);

        assertThatThrownBy(() -> service.update(PATIENT_ID, request("张三", "440300199005019999", "13800001111")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("占用");

        verify(patientMapper, never()).updateById(any(BizPatient.class));
    }

    @Test
    @DisplayName("还有未结束挂号的患者不允许删档（否则病历处方全成孤儿）")
    void deleteBlockedWhenActiveRegistrationExists() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());
        when(patientMapper.countActiveRegistration(PATIENT_ID)).thenReturn(1);

        assertThatThrownBy(() -> service.delete(PATIENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未结束的挂号");

        verify(patientMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("没有未结束挂号时可以正常删除（逻辑删除）")
    void deleteAllowedWithoutActiveRegistration() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());
        when(patientMapper.countActiveRegistration(PATIENT_ID)).thenReturn(0);

        service.delete(PATIENT_ID);

        verify(patientMapper).deleteById(PATIENT_ID);
    }

    @Test
    @DisplayName("列表返回脱敏后的身份证与手机号，且不带病史")
    void pageReturnsMaskedAndTrimmedData() {
        Page<BizPatient> page = new Page<>(1, 10);
        page.setRecords(List.of(entity()));
        page.setTotal(1);
        when(patientMapper.selectPage(any(), any())).thenReturn(page);

        PatientQuery query = new PatientQuery();
        PageResult<PatientVO> result = service.page(query);

        PatientVO vo = result.getList().get(0);
        assertThat(vo.getIdCard()).isEqualTo("4403**********1234");
        assertThat(vo.getPhone()).isEqualTo("138****1111");
        // 列表不返回病史，减少不必要的信息暴露
        assertThat(vo.getMedicalHistory()).isNull();
        assertThat(vo.getFamilyHistory()).isNull();
        assertThat(vo.getAge()).isNotNull();
    }

    @Test
    @DisplayName("详情返回病史（医生看诊需要）")
    void detailIncludesHistory() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());

        PatientVO vo = service.detail(PATIENT_ID);

        assertThat(vo.getMedicalHistory()).isEqualTo("高血压 5 年");
        assertThat(vo.getAllergyHistory()).isEqualTo("青霉素过敏");
        // 即使详情页，身份证与手机号仍脱敏，明文要走单独的解密接口
        assertThat(vo.getIdCard()).isEqualTo("4403**********1234");
    }

    @Test
    @DisplayName("解密查看返回明文（该接口另有权限与审计）")
    void plainReturnsRawValues() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(entity());

        var plain = service.plain(PATIENT_ID);

        assertThat(plain.getIdCard()).isEqualTo("440300199005011234");
        assertThat(plain.getPhone()).isEqualTo("13800001111");
    }

    @Test
    @DisplayName("身份证查重接口按 hash 判定")
    void idCardExistsUsesHash() {
        when(patientMapper.countByIdCardHash(HashUtil.sha256Hex("440300199005011234"))).thenReturn(1);

        assertThat(service.idCardExists("440300199005011234")).isTrue();
        assertThat(service.idCardExists("440300199005010000")).isFalse();
    }

    @Test
    @DisplayName("患者不存在时抛出 40400 而不是空指针")
    void missingPatientIsReportedAsNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(PATIENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }
}
