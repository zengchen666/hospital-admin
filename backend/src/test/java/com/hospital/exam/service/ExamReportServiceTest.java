package com.hospital.exam.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.exam.dto.ExamReportVO;
import com.hospital.exam.dto.ExamResultItemRequest;
import com.hospital.exam.dto.ExamResultRequest;
import com.hospital.exam.entity.BizExamApply;
import com.hospital.exam.entity.BizExamItem;
import com.hospital.exam.entity.BizExamReport;
import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamItemMapper;
import com.hospital.exam.mapper.BizExamReportMapper;
import com.hospital.message.service.MessageService;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检查报告：录入、发布锁定、危急值、防串单、审核制衡。
 *
 * <p>报告是临床做决策的依据，所以「发布后不可改」「不能自审」「结果不能写到别人的单子上」
 * 这三条必须钉死；危急值更是直接关系患者安全。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamReportServiceTest {

    private static final Long APPLY_ID = 5001L;
    private static final Long REPORT_ID = 6001L;
    private static final Long ITEM_ID = 7001L;
    private static final Long REPORTER_ID = 9L;
    private static final Long AUDITOR_ID = 8L;

    @Mock
    private BizExamReportMapper reportMapper;
    @Mock
    private BizExamItemMapper itemMapper;
    @Mock
    private BizExamApplyMapper applyMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ExamNoGenerator noGenerator;
    @Mock
    private MessageService messageService;

    private ExamReportService service;

    @BeforeEach
    void setUp() {
        service = new ExamReportService(reportMapper, itemMapper, applyMapper,
                patientMapper, userMapper, noGenerator, messageService);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizExamReport entity = inv.getArgument(0);
            entity.setId(REPORT_ID);
            return 1;
        }).when(reportMapper).insert(any(BizExamReport.class));
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

    private BizExamApply apply(int status) {
        BizExamApply entity = new BizExamApply();
        entity.setId(APPLY_ID);
        entity.setApplyNo("EXM202610260001");
        entity.setPatientId(100L);
        entity.setStatus(status);
        return entity;
    }

    private BizExamItem item() {
        BizExamItem entity = new BizExamItem();
        entity.setId(ITEM_ID);
        entity.setApplyId(APPLY_ID);
        entity.setItemCode("WBC");
        entity.setItemName("白细胞计数");
        return entity;
    }

    private ExamResultItemRequest resultItem(String value, int flag) {
        ExamResultItemRequest item = new ExamResultItemRequest();
        item.setItemId(ITEM_ID);
        item.setResultValue(value);
        item.setRefRange("4.0-10.0");
        item.setUnit("10^9/L");
        item.setAbnormalFlag(flag);
        return item;
    }

    private ExamResultRequest request(Integer critical, String value) {
        ExamResultRequest request = new ExamResultRequest();
        request.setApplyId(APPLY_ID);
        request.setResult("白细胞升高");
        request.setConclusion("提示细菌感染");
        request.setIsCritical(critical);
        request.setItems(List.of(resultItem(value, 1)));
        return request;
    }

    @Test
    @DisplayName("检查单不存在或已出报告时不能录入")
    void applyMustBeOpen() {
        loginAs(REPORTER_ID, "lab");

        when(applyMapper.selectById(APPLY_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.save(request(0, "12.5")))
                .hasMessageContaining("检查单不存在");

        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(3));
        assertThatThrownBy(() -> service.save(request(0, "12.5")))
                .hasMessageContaining("已出报告");
    }

    @Test
    @DisplayName("首次录入生成报告草稿，不改变申请单状态")
    void firstSaveCreatesDraft() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2));
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(null);
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        when(noGenerator.nextReportNo()).thenReturn("RPT202610260001");
        loginAs(REPORTER_ID, "lab");

        Long id = service.save(request(0, "12.5"));

        assertThat(id).isEqualTo(REPORT_ID);
        ArgumentCaptor<BizExamReport> captor = ArgumentCaptor.forClass(BizExamReport.class);
        verify(reportMapper).insert(captor.capture());
        BizExamReport saved = captor.getValue();
        assertThat(saved.getReportNo()).isEqualTo("RPT202610260001");
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getReporterId()).isEqualTo(REPORTER_ID);
        // 草稿阶段不推进申请单，临床此时不该看到「已出报告」
        verify(applyMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("结果只能写到本检查单的明细上（防串单）")
    void resultMustBelongToSameApply() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2));
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(null);
        // 该申请单下只有 ITEM_ID 一条，请求却传了别的 id
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        loginAs(REPORTER_ID, "lab");

        ExamResultRequest foreign = request(0, "12.5");
        foreign.getItems().get(0).setItemId(9999L);

        assertThatThrownBy(() -> service.save(foreign))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该检查单");

        // 报告本身可以插入（事务会回滚），但结果绝不能落到别的单上
        verify(itemMapper, never()).updateById(any(BizExamItem.class));
    }

    @Test
    @DisplayName("草稿可反复覆盖，不产生第二份报告")
    void secondSaveUpdatesDraft() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2));
        BizExamReport draft = new BizExamReport();
        draft.setId(REPORT_ID);
        draft.setStatus(0);
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(draft);
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        loginAs(REPORTER_ID, "lab");

        service.save(request(0, "10.0"));

        verify(reportMapper).updateById(any(BizExamReport.class));
        verify(reportMapper, never()).insert(any(BizExamReport.class));
    }

    @Test
    @DisplayName("已发布的报告不允许再改")
    void publishedReportIsLocked() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2));
        BizExamReport published = new BizExamReport();
        published.setId(REPORT_ID);
        published.setStatus(1);
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(published);
        loginAs(REPORTER_ID, "lab");

        assertThatThrownBy(() -> service.save(request(0, "9.0")))
                .hasMessageContaining("已发布");

        verify(reportMapper, never()).updateById(any(BizExamReport.class));
    }

    @Test
    @DisplayName("危急值标记规范化：非 1 一律存 0")
    void criticalFlagIsNormalized() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2));
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(null);
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        when(noGenerator.nextReportNo()).thenReturn("RPT202610260002");
        loginAs(REPORTER_ID, "lab");

        service.save(request(null, "12.5"));

        ArgumentCaptor<BizExamReport> captor = ArgumentCaptor.forClass(BizExamReport.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsCritical()).isZero();
    }

    @Test
    @DisplayName("审核发布：报告 0→1 且申请单推进到已出报告")
    void auditPublishesReportAndClosesApply() {
        BizExamReport draft = new BizExamReport();
        draft.setId(REPORT_ID);
        draft.setApplyId(APPLY_ID);
        draft.setStatus(0);
        draft.setReporterId(REPORTER_ID);
        draft.setIsCritical(0);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(draft);
        when(reportMapper.transitStatus(REPORT_ID, 0, 1, AUDITOR_ID)).thenReturn(1);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2));
        when(applyMapper.transitStatus(APPLY_ID, 2, 3, AUDITOR_ID)).thenReturn(1);
        loginAs(AUDITOR_ID, "director");

        service.audit(REPORT_ID);

        verify(reportMapper).transitStatus(REPORT_ID, 0, 1, AUDITOR_ID);
        verify(applyMapper).transitStatus(APPLY_ID, 2, 3, AUDITOR_ID);
    }

    @Test
    @DisplayName("不能审核自己录入的报告（与处方审核同样的制衡）")
    void reporterCannotAuditOwnReport() {
        BizExamReport draft = new BizExamReport();
        draft.setId(REPORT_ID);
        draft.setStatus(0);
        draft.setReporterId(REPORTER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(draft);
        loginAs(REPORTER_ID, "lab");

        assertThatThrownBy(() -> service.audit(REPORT_ID))
                .hasMessageContaining("不能审核自己");

        verify(reportMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("重复发布返回冲突，不会二次推进申请单")
    void duplicateAuditIsRejected() {
        BizExamReport draft = new BizExamReport();
        draft.setId(REPORT_ID);
        draft.setApplyId(APPLY_ID);
        draft.setStatus(0);
        draft.setReporterId(REPORTER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(draft);
        when(reportMapper.transitStatus(eq(REPORT_ID), any(), any(), any())).thenReturn(0);
        loginAs(AUDITOR_ID, "director");

        assertThatThrownBy(() -> service.audit(REPORT_ID))
                .hasMessageContaining("状态已变化");

        verify(applyMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("未录入报告的检查单查询返回 null，而不是抛异常")
    void missingReportReturnsNull() {
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(null);

        assertThat(service.getByApply(APPLY_ID)).isNull();
    }

    /* ---------------- 报告详情 / 危急值闭环（本轮新增） ---------------- */

    private BizExamReport report(int status, int critical, int handleStatus) {
        BizExamReport report = new BizExamReport();
        report.setId(REPORT_ID);
        report.setApplyId(APPLY_ID);
        report.setReportNo("RPT202610270001");
        report.setStatus(status);
        report.setIsCritical(critical);
        report.setReporterId(REPORTER_ID);
        report.setHandleStatus(handleStatus);
        return report;
    }

    @Test
    @DisplayName("报告详情：不存在的 ID 直接 404，而不是返回空对象")
    void detailThrowsWhenMissing() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(REPORT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报告不存在");
    }

    @Test
    @DisplayName("报告详情：装配患者名、录入者与审核者")
    void detailAssemblesNames() {
        BizExamReport published = report(1, 0, 0);
        published.setAuditorId(AUDITOR_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(published);
        when(applyMapper.selectByIds(any())).thenReturn(List.of(apply(3)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        BizPatient patient = new BizPatient();
        patient.setId(100L);
        patient.setName("李四");
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient));
        SysUser reporter = new SysUser();
        reporter.setId(REPORTER_ID);
        reporter.setRealName("陈技师");
        SysUser auditor = new SysUser();
        auditor.setId(AUDITOR_ID);
        auditor.setRealName("周院长");
        when(userMapper.selectByIds(any())).thenReturn(List.of(reporter, auditor));

        ExamReportVO vo = service.detail(REPORT_ID);

        assertThat(vo.getReportNo()).isEqualTo("RPT202610270001");
        assertThat(vo.getPatientName()).isEqualTo("李四");
        assertThat(vo.getReporterName()).isEqualTo("陈技师");
        assertThat(vo.getAuditorName()).isEqualTo("周院长");
        assertThat(vo.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("危急值处理：非危急值报告无需处理")
    void handleCriticalRejectsNormalReport() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report(1, 0, 0));
        loginAs(AUDITOR_ID, "doctor");

        assertThatThrownBy(() -> service.handleCritical(REPORT_ID, "已通知"))
                .hasMessageContaining("不是危急值");
        verify(reportMapper, never()).handleCritical(anyLong(), any(), any());
    }

    @Test
    @DisplayName("危急值处理：草稿阶段的危急值不能登记处理")
    void handleCriticalRejectsDraft() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report(0, 1, 0));
        loginAs(AUDITOR_ID, "doctor");

        assertThatThrownBy(() -> service.handleCritical(REPORT_ID, "已通知"))
                .hasMessageContaining("尚未发布");
    }

    @Test
    @DisplayName("危急值处理：处理措施为空不留痕，直接拒绝")
    void handleCriticalRejectsBlankRemark() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report(1, 1, 0));
        loginAs(AUDITOR_ID, "doctor");

        assertThatThrownBy(() -> service.handleCritical(REPORT_ID, "   "))
                .hasMessageContaining("处理措施不能为空");
    }

    @Test
    @DisplayName("危急值处理：重复登记被拒")
    void handleCriticalRejectsDuplicate() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report(1, 1, 1));
        loginAs(AUDITOR_ID, "doctor");

        assertThatThrownBy(() -> service.handleCritical(REPORT_ID, "再处理一次"))
                .hasMessageContaining("已处理");
    }

    @Test
    @DisplayName("危急值处理：登记成功并记录操作人")
    void handleCriticalSucceeds() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report(1, 1, 0));
        when(reportMapper.handleCritical(eq(REPORT_ID), eq(AUDITOR_ID), eq("已电话通知患者返院")))
                .thenReturn(1);
        loginAs(AUDITOR_ID, "doctor");

        service.handleCritical(REPORT_ID, " 已电话通知患者返院 ");

        // 措施做了 trim：首尾空白不进库，避免留下「看起来填了其实没填」的记录
        verify(reportMapper).handleCritical(REPORT_ID, AUDITOR_ID, "已电话通知患者返院");
    }

    @Test
    @DisplayName("危急值处理：并发下条件更新命中 0 行返回冲突")
    void handleCriticalConflict() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report(1, 1, 0));
        when(reportMapper.handleCritical(anyLong(), any(), any())).thenReturn(0);
        loginAs(AUDITOR_ID, "doctor");

        assertThatThrownBy(() -> service.handleCritical(REPORT_ID, "已通知"))
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("发布危急值报告时主动通知申请医生")
    void publishingCriticalNotifiesDoctor() {
        BizExamApply target = apply(2);
        target.setDoctorId(3L);
        target.setExamItem("血钾");

        BizExamReport draft = report(0, 1, 0);
        draft.setReporterId(REPORTER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(draft);
        when(reportMapper.transitStatus(REPORT_ID, 0, 1, AUDITOR_ID)).thenReturn(1);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(target);
        when(applyMapper.transitStatus(APPLY_ID, 2, 3, AUDITOR_ID)).thenReturn(1);
        loginAs(AUDITOR_ID, "director");

        service.audit(REPORT_ID);

        verify(messageService).sendTo(eq(3L), any(), any(),
                eq(MessageService.TYPE_WARNING), eq("exam"), eq(REPORT_ID));
    }

    @Test
    @DisplayName("通知渠道异常不影响报告发布（不能把小故障放大成事故）")
    void notificationFailureDoesNotRollbackPublish() {
        BizExamApply target = apply(2);
        target.setDoctorId(3L);
        target.setExamItem("血钾");

        BizExamReport draft = report(0, 1, 0);
        draft.setReporterId(REPORTER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(draft);
        when(reportMapper.transitStatus(REPORT_ID, 0, 1, AUDITOR_ID)).thenReturn(1);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(target);
        when(applyMapper.transitStatus(APPLY_ID, 2, 3, AUDITOR_ID)).thenReturn(1);
        when(messageService.sendTo(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("消息服务不可用"));
        loginAs(AUDITOR_ID, "director");

        service.audit(REPORT_ID);

        // 发布已经生效：申请单必须推进，不因通知失败回退
        verify(applyMapper).transitStatus(APPLY_ID, 2, 3, AUDITOR_ID);
    }
}
