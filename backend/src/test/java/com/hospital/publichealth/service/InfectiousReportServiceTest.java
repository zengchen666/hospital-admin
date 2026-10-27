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
import com.hospital.publichealth.dto.InfectiousQuery;
import com.hospital.publichealth.dto.InfectiousRequest;
import com.hospital.publichealth.dto.InfectiousVO;
import com.hospital.publichealth.entity.BizInfectiousReport;
import com.hospital.publichealth.mapper.BizInfectiousReportMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 传染病报告卡。
 *
 * <p>这不是普通单据，是<b>法定文书</b>：报告日期不能是未来、同一病种不能重复填卡、
 * 上报后不可再改只能追加反馈。三条规则都要有测试守着。</p>
 */
@ExtendWith(MockitoExtension.class)
class InfectiousReportServiceTest {

    private static final Long PATIENT_ID = 100L;
    private static final Long CARD_ID = 6201L;
    private static final Long DOCTOR_ID = 3L;
    private static final String DISEASE = "新型冠状病毒感染";

    @Mock
    private BizInfectiousReportMapper reportMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;

    private InfectiousReportService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizInfectiousReport.class);
        TableInfoHelper.initTableInfo(assistant, BizPatient.class);
    }

    @BeforeEach
    void setUp() {
        service = new InfectiousReportService(reportMapper, patientMapper, userMapper);
        loginAs(DOCTOR_ID);
        lenient().doAnswer(inv -> {
            BizInfectiousReport entity = inv.getArgument(0);
            entity.setId(CARD_ID);
            return 1;
        }).when(reportMapper).insert(any(BizInfectiousReport.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("患者不存在时拒绝填卡（40400）")
    void patientNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertCode(() -> service.create(request()), ErrorCode.NO_RESOURCE);
        verify(reportMapper, never()).insert(any(BizInfectiousReport.class));
    }

    @Test
    @DisplayName("报告日期不能是未来（40001）—— 那是伪造上报时间")
    void futureReportDateRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());

        InfectiousRequest request = request();
        request.setReportDate(LocalDate.now().plusDays(1));

        assertCode(() -> service.create(request), ErrorCode.PARAM_INVALID);
        verify(reportMapper, never()).insert(any(BizInfectiousReport.class));
    }

    @Test
    @DisplayName("同一患者同一病种不能重复填卡（40904）")
    void duplicatePendingRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(reportMapper.countPending(PATIENT_ID, DISEASE)).thenReturn(1);

        assertCode(() -> service.create(request()), ErrorCode.DUPLICATE_DATA);
        verify(reportMapper, never()).insert(any(BizInfectiousReport.class));
    }

    @Test
    @DisplayName("填卡成功：落为 0 待上报，记录填卡人")
    void createSuccess() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(reportMapper.countPending(PATIENT_ID, DISEASE)).thenReturn(0);

        Long id = service.create(request());

        assertThat(id).isEqualTo(CARD_ID);
        ArgumentCaptor<BizInfectiousReport> captor = ArgumentCaptor.forClass(BizInfectiousReport.class);
        verify(reportMapper).insert(captor.capture());
        BizInfectiousReport saved = captor.getValue();
        assertThat(saved.getReportStatus()).isZero();
        assertThat(saved.getReporterId()).isEqualTo(DOCTOR_ID);
    }

    @Test
    @DisplayName("上报成功：调用带状态条件的更新，记录上报人")
    void reportSuccess() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(entity(0));
        when(reportMapper.report(CARD_ID, DOCTOR_ID)).thenReturn(1);

        service.report(CARD_ID);

        verify(reportMapper).report(CARD_ID, DOCTOR_ID);
    }

    @Test
    @DisplayName("已上报的卡片不能重复上报（40902）")
    void alreadyReported() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(entity(1));

        assertCode(() -> service.report(CARD_ID), ErrorCode.STATUS_NOT_ALLOWED);
        verify(reportMapper, never()).report(any(), any());
    }

    @Test
    @DisplayName("并发送重时条件更新返回 0 → 40900，不会向疾控发两遍")
    void concurrentReportConflict() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(entity(0));
        when(reportMapper.report(CARD_ID, DOCTOR_ID)).thenReturn(0);

        assertCode(() -> service.report(CARD_ID), ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("上报不存在的卡片报 40400")
    void reportMissing() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(null);

        assertCode(() -> service.report(CARD_ID), ErrorCode.NO_RESOURCE);
    }

    @Test
    @DisplayName("疾控反馈不能为空（40001）")
    void emptyFeedbackRejected() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(entity(1));

        assertCode(() -> service.feedback(CARD_ID, "  "), ErrorCode.PARAM_INVALID);
        verify(reportMapper, never()).feedback(any(), any(), any());
    }

    @Test
    @DisplayName("反馈只追加内容，不碰病名与日期")
    void feedbackAppends() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(entity(1));
        when(reportMapper.feedback(CARD_ID, "已纳入随访", DOCTOR_ID)).thenReturn(1);

        service.feedback(CARD_ID, "已纳入随访");

        verify(reportMapper).feedback(CARD_ID, "已纳入随访", DOCTOR_ID);
    }

    @Test
    @DisplayName("已上报的卡片超期小时数为 0")
    void reportedHasNoOverdue() {
        when(reportMapper.selectById(CARD_ID)).thenReturn(entity(1));
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(doctor()));

        InfectiousVO vo = service.detail(CARD_ID);

        assertThat(vo.getOverdueHours()).isZero();
    }

    @Test
    @DisplayName("待上报且填卡已过 48 小时 → 超期 24 小时")
    void pendingOverdueBy24Hours() {
        BizInfectiousReport card = entity(0);
        card.setCreateTime(LocalDateTime.now().minusHours(48));
        when(reportMapper.selectById(CARD_ID)).thenReturn(card);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(doctor()));

        InfectiousVO vo = service.detail(CARD_ID);

        // 法定时限一期统一按 24 小时核算，48 - 24 = 24
        assertThat(vo.getOverdueHours()).isEqualTo(24L);
    }

    @Test
    @DisplayName("待上报但仍在时限内 → 超期为 0")
    void pendingWithinDeadline() {
        BizInfectiousReport card = entity(0);
        card.setCreateTime(LocalDateTime.now().minusHours(3));
        when(reportMapper.selectById(CARD_ID)).thenReturn(card);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(doctor()));

        assertThat(service.detail(CARD_ID).getOverdueHours()).isZero();
    }

    @Test
    @DisplayName("列表补齐患者与填卡医生姓名")
    void pageResolvesNames() {
        Page<BizInfectiousReport> page = new Page<>(1, 10);
        page.setRecords(List.of(entity(0)));
        page.setTotal(1);
        when(reportMapper.selectPage(any(), any())).thenReturn(page);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(doctor()));

        PageResult<InfectiousVO> result = service.page(new InfectiousQuery());

        InfectiousVO vo = result.getList().get(0);
        assertThat(vo.getPatientName()).isEqualTo("郑新患");
        assertThat(vo.getReporterName()).isEqualTo("张医生");
    }

    /* ---------------- 辅助 ---------------- */

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("doc01").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPatient patient() {
        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("郑新患");
        return patient;
    }

    private SysUser doctor() {
        SysUser user = new SysUser();
        user.setId(DOCTOR_ID);
        user.setRealName("张医生");
        return user;
    }

    private BizInfectiousReport entity(Integer status) {
        BizInfectiousReport entity = new BizInfectiousReport();
        entity.setId(CARD_ID);
        entity.setPatientId(PATIENT_ID);
        entity.setDiseaseName(DISEASE);
        entity.setIcdCode("U07.1");
        entity.setReportDate(LocalDate.now());
        entity.setReporterId(DOCTOR_ID);
        entity.setReportStatus(status);
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    private InfectiousRequest request() {
        InfectiousRequest request = new InfectiousRequest();
        request.setPatientId(PATIENT_ID);
        request.setDiseaseName(DISEASE);
        request.setIcdCode("U07.1");
        request.setReportDate(LocalDate.now());
        return request;
    }
}
