package com.hospital.publichealth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.publichealth.dto.HealthRecordQuery;
import com.hospital.publichealth.dto.HealthRecordRequest;
import com.hospital.publichealth.dto.HealthRecordVO;
import com.hospital.publichealth.entity.BizHealthRecord;
import com.hospital.publichealth.mapper.BizHealthRecordMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 居民健康档案：建档、更新、分页。
 *
 * <p>要守住的核心：<b>一人一份档案</b>。出现两份，同一个人的随访与体检结论就会分散，
 * 慢病管理的年度统计直接失真。</p>
 */
@ExtendWith(MockitoExtension.class)
class HealthRecordServiceTest {

    private static final Long PATIENT_ID = 100L;
    private static final Long RECORD_ID = 6001L;
    private static final Long OPERATOR_ID = 8L;

    @Mock
    private BizHealthRecordMapper recordMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private HealthRecordNoGenerator noGenerator;

    private HealthRecordService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizHealthRecord.class);
        TableInfoHelper.initTableInfo(assistant, BizPatient.class);
    }

    @BeforeEach
    void setUp() {
        service = new HealthRecordService(recordMapper, patientMapper, noGenerator);
        loginAs(OPERATOR_ID);
        lenient().doAnswer(inv -> {
            BizHealthRecord entity = inv.getArgument(0);
            entity.setId(RECORD_ID);
            return 1;
        }).when(recordMapper).insert(any(BizHealthRecord.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("患者不存在时拒绝建档（40400）")
    void patientNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertCode(() -> service.create(request()), ErrorCode.NO_RESOURCE);
        verify(recordMapper, never()).insert(any(BizHealthRecord.class));
    }

    @Test
    @DisplayName("已建档的居民不能重复建档（40904）")
    void duplicateRecord() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(recordMapper.countByPatient(PATIENT_ID)).thenReturn(1);

        assertCode(() -> service.create(request()), ErrorCode.DUPLICATE_DATA);
        verify(recordMapper, never()).insert(any(BizHealthRecord.class));
    }

    @Test
    @DisplayName("建档成功：生成档案号，吸烟饮酒默认 0，记录创建人")
    void createSuccess() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(recordMapper.countByPatient(PATIENT_ID)).thenReturn(0);
        when(noGenerator.next()).thenReturn("HR202610270001");

        Long id = service.create(request());

        assertThat(id).isEqualTo(RECORD_ID);
        ArgumentCaptor<BizHealthRecord> captor = ArgumentCaptor.forClass(BizHealthRecord.class);
        verify(recordMapper).insert(captor.capture());
        BizHealthRecord saved = captor.getValue();
        assertThat(saved.getRecordNo()).isEqualTo("HR202610270001");
        // 不传就按「否」记，绝不留 null —— 统计吸烟率时 null 会被当成缺失样本
        assertThat(saved.getSmoking()).isZero();
        assertThat(saved.getDrinking()).isZero();
        assertThat(saved.getCreateBy()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("更新档案不换档案号")
    void updateKeepsRecordNo() {
        when(recordMapper.selectById(RECORD_ID)).thenReturn(entity());

        HealthRecordRequest request = request();
        request.setChronicDisease("高血压,糖尿病");
        request.setSmoking(1);
        service.update(RECORD_ID, request);

        ArgumentCaptor<BizHealthRecord> captor = ArgumentCaptor.forClass(BizHealthRecord.class);
        verify(recordMapper).updateById(captor.capture());
        BizHealthRecord updated = captor.getValue();
        assertThat(updated.getRecordNo()).isEqualTo("HR202610260001");
        assertThat(updated.getChronicDisease()).isEqualTo("高血压,糖尿病");
        assertThat(updated.getSmoking()).isEqualTo(1);
        assertThat(updated.getUpdateBy()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("更新不存在的档案报 40400")
    void updateMissing() {
        when(recordMapper.selectById(RECORD_ID)).thenReturn(null);

        assertCode(() -> service.update(RECORD_ID, request()), ErrorCode.NO_RESOURCE);
    }

    @Test
    @DisplayName("列表按出生日期实时算年龄，不用缓存字段")
    void pageCalculatesAge() {
        Page<BizHealthRecord> page = new Page<>(1, 10);
        page.setRecords(List.of(entity()));
        page.setTotal(1);
        when(recordMapper.selectPage(any(), any())).thenReturn(page);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));

        HealthRecordQuery query = new HealthRecordQuery();
        PageResult<HealthRecordVO> result = service.page(query);

        HealthRecordVO vo = result.getList().get(0);
        assertThat(vo.getPatientName()).isEqualTo("郑新患");
        assertThat(vo.getAge()).isEqualTo(LocalDate.now().getYear() - 1990);
        assertThat(vo.getRecordNo()).isEqualTo("HR202610260001");
    }

    @Test
    @DisplayName("关键字按居民姓名匹配档案")
    void pageByPatientName() {
        Page<BizHealthRecord> page = new Page<>(1, 10);
        page.setRecords(List.of(entity()));
        page.setTotal(1);
        when(recordMapper.selectPage(any(), any())).thenReturn(page);
        when(patientMapper.selectList(any())).thenReturn(List.of(patient()));
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));

        HealthRecordQuery query = new HealthRecordQuery();
        query.setKeyword("郑新患");
        PageResult<HealthRecordVO> result = service.page(query);

        assertThat(result.getList()).hasSize(1);
        verify(patientMapper).selectList(any());
    }

    @Test
    @DisplayName("详情不存在时报 40400")
    void detailMissing() {
        when(recordMapper.selectById(RECORD_ID)).thenReturn(null);

        assertCode(() -> service.detail(RECORD_ID), ErrorCode.NO_RESOURCE);
    }

    /* ---------------- 辅助 ---------------- */

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("tester").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPatient patient() {
        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("郑新患");
        patient.setGender(1);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        return patient;
    }

    private BizHealthRecord entity() {
        BizHealthRecord record = new BizHealthRecord();
        record.setId(RECORD_ID);
        record.setPatientId(PATIENT_ID);
        record.setRecordNo("HR202610260001");
        record.setChronicDisease("高血压");
        record.setSmoking(0);
        record.setDrinking(0);
        record.setExercise("每周 3 次");
        return record;
    }

    private HealthRecordRequest request() {
        HealthRecordRequest request = new HealthRecordRequest();
        request.setPatientId(PATIENT_ID);
        request.setChronicDisease("高血压");
        request.setExercise("每周 3 次");
        return request;
    }
}
