package com.hospital.inpatient.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.inpatient.dto.NursingQuery;
import com.hospital.inpatient.dto.NursingRequest;
import com.hospital.inpatient.dto.NursingVO;
import com.hospital.inpatient.entity.BizAdmission;
import com.hospital.inpatient.entity.BizBed;
import com.hospital.inpatient.entity.BizNursingRecord;
import com.hospital.inpatient.mapper.BizAdmissionMapper;
import com.hospital.inpatient.mapper.BizBedMapper;
import com.hospital.inpatient.mapper.BizNursingRecordMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.entity.SysUser;
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
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 护理记录：体温单 / 生命体征 / 护理记录三类共用一张表。
 *
 * <p>本类守住三件事：</p>
 * <ol>
 *   <li><b>类型与必填字段挂钩</b> —— 体温单没有体温就是一条废数据；</li>
 *   <li><b>出院患者不能再补录</b> —— 出院后往回写体征，性质上是改病历；</li>
 *   <li><b>查询必须带 record_type</b> —— 这是「一张表三类记录」这个取舍的唯一代价，
 *       漏了它体温曲线上会混进纯文本护理记录，而且看起来不像 bug。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class NursingServiceTest {

    private static final Long ADMISSION_ID = 7001L;
    private static final Long PATIENT_ID = 100L;
    private static final Long BED_ID = 8201L;
    private static final Long NURSE_ID = 66L;

    @Mock
    private BizNursingRecordMapper recordMapper;
    @Mock
    private BizAdmissionMapper admissionMapper;
    @Mock
    private BizBedMapper bedMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;

    private NursingService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizNursingRecord.class);
        TableInfoHelper.initTableInfo(assistant, BizAdmission.class);
        TableInfoHelper.initTableInfo(assistant, BizBed.class);
        TableInfoHelper.initTableInfo(assistant, BizPatient.class);
    }

    @BeforeEach
    void setUp() {
        service = new NursingService(recordMapper, admissionMapper, bedMapper, patientMapper, userMapper);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizNursingRecord entity = inv.getArgument(0);
            entity.setId(9001L);
            return 1;
        }).when(recordMapper).insert(any(BizNursingRecord.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("nurse01")
                .roleCodes(new LinkedHashSet<>(Set.of("nurse")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizAdmission admission(Integer status) {
        BizAdmission admission = new BizAdmission();
        admission.setId(ADMISSION_ID);
        admission.setPatientId(PATIENT_ID);
        admission.setBedId(BED_ID);
        admission.setStatus(status);
        admission.setAdmissionTime(LocalDateTime.now().minusDays(2));
        return admission;
    }

    private NursingRequest nursing(Integer type) {
        NursingRequest request = new NursingRequest();
        request.setAdmissionId(ADMISSION_ID);
        request.setRecordType(type);
        return request;
    }

    @Test
    @DisplayName("体温单：带体温可保存，且记录护士取当前登录用户")
    void addTemperatureSheet() {
        loginAs(NURSE_ID);
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));

        NursingRequest request = nursing(BizNursingRecord.TYPE_TEMPERATURE);
        request.setTemperature(new BigDecimal("38.6"));
        request.setPulse(96);

        Long id = service.add(request);

        assertThat(id).isEqualTo(9001L);
        ArgumentCaptor<BizNursingRecord> captor = ArgumentCaptor.forClass(BizNursingRecord.class);
        org.mockito.Mockito.verify(recordMapper).insert(captor.capture());
        BizNursingRecord saved = captor.getValue();
        assertThat(saved.getNurseId()).isEqualTo(NURSE_ID);
        assertThat(saved.getRecordType()).isEqualTo(BizNursingRecord.TYPE_TEMPERATURE);
        assertThat(saved.getRecordTime()).isNotNull();
    }

    @Test
    @DisplayName("体温单缺体温 -> 40001（没有体温的体温单是废数据）")
    void temperatureSheetRequiresTemperature() {
        loginAs(NURSE_ID);
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));

        NursingRequest request = nursing(BizNursingRecord.TYPE_TEMPERATURE);
        request.setPulse(96);

        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
    }

    @Test
    @DisplayName("护理记录缺内容 -> 40001")
    void nursingRecordRequiresContent() {
        loginAs(NURSE_ID);
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));

        assertThatThrownBy(() -> service.add(nursing(BizNursingRecord.TYPE_NURSING)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
    }

    @Test
    @DisplayName("生命体征至少要有一项体征值")
    void vitalSignsRequireAtLeastOneValue() {
        loginAs(NURSE_ID);
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));

        assertThatThrownBy(() -> service.add(nursing(BizNursingRecord.TYPE_VITAL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);

        NursingRequest ok = nursing(BizNursingRecord.TYPE_VITAL);
        ok.setSpo2(98);
        assertThat(service.add(ok)).isEqualTo(9001L);
    }

    @Test
    @DisplayName("已出院患者不能补录护理记录 -> 40902")
    void cannotRecordAfterDischarge() {
        loginAs(NURSE_ID);
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(1));

        NursingRequest request = nursing(BizNursingRecord.TYPE_NURSING);
        request.setContent("补录");

        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40902);
    }

    @Test
    @DisplayName("住院记录不存在 -> 40400")
    void admissionNotFound() {
        loginAs(NURSE_ID);
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.add(nursing(BizNursingRecord.TYPE_NURSING)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    @Test
    @DisplayName("分页查询必须带上 record_type 条件")
    void pageMustFilterByRecordType() {
        NursingQuery query = new NursingQuery();
        query.setAdmissionId(ADMISSION_ID);
        query.setRecordType(BizNursingRecord.TYPE_TEMPERATURE);

        BizNursingRecord row = new BizNursingRecord();
        row.setId(1L);
        row.setAdmissionId(ADMISSION_ID);
        row.setRecordType(BizNursingRecord.TYPE_TEMPERATURE);
        row.setTemperature(new BigDecimal("37.8"));
        row.setRecordTime(LocalDateTime.now());
        row.setNurseId(NURSE_ID);

        Page<BizNursingRecord> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(recordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(admissionMapper.selectByIds(any())).thenReturn(List.of(admission(0)));
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(bedMapper.selectByIds(any())).thenReturn(List.of(bed()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(nurse()));

        PageResult<NursingVO> result = service.page(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList().get(0).getPatientName()).isEqualTo("郑新患");
        assertThat(result.getList().get(0).getBedNo()).isEqualTo("A01");
        assertThat(result.getList().get(0).getNurseName()).isEqualTo("林护士");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<BizNursingRecord>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(recordMapper).selectPage(any(Page.class), captor.capture());
        String segment = captor.getValue().getSqlSegment();
        assertThat(segment).contains("record_type");
        assertThat(segment).contains("admission_id");
    }

    private BizPatient patient() {
        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("郑新患");
        return patient;
    }

    private BizBed bed() {
        BizBed bed = new BizBed();
        bed.setId(BED_ID);
        bed.setBedNo("A01");
        return bed;
    }

       private SysUser nurse() {
        SysUser user = new SysUser();
        user.setId(NURSE_ID);
        user.setRealName("林护士");
        return user;
    }

    @Test
    @DisplayName("空结果不查关联表（避免无意义的 selectByIds）")
    void emptyResultSkipsJoins() {
        NursingQuery query = new NursingQuery();
        query.setRecordType(BizNursingRecord.TYPE_NURSING);

        Page<BizNursingRecord> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(recordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<NursingVO> result = service.page(query);

        assertThat(result.getList()).isEmpty();
        org.mockito.Mockito.verify(admissionMapper, org.mockito.Mockito.never()).selectByIds(any());
        org.mockito.Mockito.verify(patientMapper, org.mockito.Mockito.never()).selectByIds(any());
    }
}
