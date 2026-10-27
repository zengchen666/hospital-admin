package com.hospital.exam.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.exam.dto.ExamApplyRequest;
import com.hospital.exam.dto.ExamBarcodeVO;
import com.hospital.exam.dto.ExamItemRequest;
import com.hospital.exam.dto.ExamItemResultRequest;
import com.hospital.exam.dto.ExamItemVO;
import com.hospital.exam.entity.BizExamApply;
import com.hospital.exam.entity.BizExamItem;
import com.hospital.exam.entity.BizExamReport;
import com.hospital.patient.entity.BizPatient;
import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamItemMapper;
import com.hospital.exam.mapper.BizExamReportMapper;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检查申请：开单数据权限、状态推进。
 */
@ExtendWith(MockitoExtension.class)
class ExamApplyServiceTest {

    private static final Long VISIT_ID = 5001L;
    private static final Long PATIENT_ID = 100L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long OTHER_DOCTOR_ID = 4L;
    private static final Long APPLY_ID = 6001L;

    @Mock
    private BizExamApplyMapper applyMapper;
    @Mock
    private BizExamItemMapper itemMapper;
    @Mock
    private BizExamReportMapper reportMapper;
    @Mock
    private BizRegistrationMapper registrationMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ExamNoGenerator noGenerator;

    private ExamApplyService service;

    /**
     * assemble() 里用了 lambdaQuery，纯单测需要先建立 TableInfo 缓存。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizExamApply.class);
        TableInfoHelper.initTableInfo(assistant, BizExamItem.class);
        TableInfoHelper.initTableInfo(assistant, BizExamReport.class);
    }

    @BeforeEach
    void setUp() {
        service = new ExamApplyService(applyMapper, itemMapper, reportMapper, registrationMapper,
                patientMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizExamApply entity = inv.getArgument(0);
            entity.setId(APPLY_ID);
            return 1;
        }).when(applyMapper).insert(any(BizExamApply.class));
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
        return entity;
    }

    private ExamApplyRequest request() {
        ExamApplyRequest request = new ExamApplyRequest();
        request.setVisitId(VISIT_ID);
        request.setExamType(1);
        request.setExamItem("血常规");
        request.setClinicalDiagnosis("发热待查");

        ExamItemRequest wbc = new ExamItemRequest();
        wbc.setItemCode("WBC");
        wbc.setItemName("白细胞计数");
        wbc.setSort(1);
        ExamItemRequest crp = new ExamItemRequest();
        crp.setItemCode("CRP");
        crp.setItemName("C反应蛋白");
        crp.setSort(2);
        request.setItems(List.of(wbc, crp));
        return request;
    }

    @Test
    @DisplayName("挂号不存在时拒绝开单")
    void visitNotFound() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.create(request()))
                .hasMessageContaining("挂号记录不存在");

        verify(applyMapper, never()).insert(any(BizExamApply.class));
    }

    @Test
    @DisplayName("已退号的挂号不能开检查")
    void cancelledVisitIsRejected() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(3, DOCTOR_ID));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.create(request()))
                .hasMessageContaining("已退号");
    }

    @Test
    @DisplayName("医生不能替别人接诊的患者开检查（40302）")
    void cannotApplyForOthersPatient() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, OTHER_DOCTOR_ID));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能为自己接诊");

        verify(applyMapper, never()).insert(any(BizExamApply.class));
    }

    @Test
    @DisplayName("管理员可为任意医生代开")
    void adminCanApplyForAnyVisit() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, OTHER_DOCTOR_ID));
        when(noGenerator.nextApplyNo()).thenReturn("EXM202610260001");
        loginAs(1L, "admin");

        Long id = service.create(request());

        assertThat(id).isEqualTo(APPLY_ID);
        verify(applyMapper).insert(any(BizExamApply.class));
    }

    @Test
    @DisplayName("开单成功：单号生成、进入待采样、明细逐条落库")
    void createSucceeds() {
        when(registrationMapper.selectById(VISIT_ID)).thenReturn(registration(1, DOCTOR_ID));
        when(noGenerator.nextApplyNo()).thenReturn("EXM202610260001");
        loginAs(DOCTOR_ID, "doctor");

        service.create(request());

        ArgumentCaptor<BizExamApply> applyCaptor = ArgumentCaptor.forClass(BizExamApply.class);
        verify(applyMapper).insert(applyCaptor.capture());
        BizExamApply saved = applyCaptor.getValue();
        assertThat(saved.getApplyNo()).isEqualTo("EXM202610260001");
        // 开单只到「待缴费」：没收到钱不能采样，由收费成功后推进到 1
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(saved.getVisitId()).isEqualTo(VISIT_ID);
        // 申请医生取当前操作人
        assertThat(saved.getDoctorId()).isEqualTo(DOCTOR_ID);

        ArgumentCaptor<BizExamItem> itemCaptor = ArgumentCaptor.forClass(BizExamItem.class);
        verify(itemMapper, times(2)).insert(itemCaptor.capture());
        List<BizExamItem> items = itemCaptor.getAllValues();
        assertThat(items.get(0).getItemCode()).isEqualTo("WBC");
        assertThat(items.get(1).getItemCode()).isEqualTo("CRP");
        // 开单时结果为空，异常标志默认 0
        assertThat(items.get(0).getResultValue()).isNull();
        assertThat(items.get(0).getAbnormalFlag()).isZero();
        assertThat(items.get(0).getApplyId()).isEqualTo(APPLY_ID);
    }

    @Test
    @DisplayName("采样：待采样 → 检验中")
    void startTestingMovesForward() {
        BizExamApply apply = new BizExamApply();
        apply.setId(APPLY_ID);
        apply.setStatus(1);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply);
        when(applyMapper.transitStatus(eq(APPLY_ID), eq(1), eq(2), any())).thenReturn(1);
        loginAs(8L, "lab");

        service.startTesting(APPLY_ID);

        verify(applyMapper).transitStatus(APPLY_ID, 1, 2, 8L);
    }

    @Test
    @DisplayName("未缴费的检查单不能采样（40902）")
    void startTestingRejectsUnpaidApply() {
        BizExamApply unpaid = new BizExamApply();
        unpaid.setId(APPLY_ID);
        unpaid.setStatus(0);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(unpaid);
        loginAs(8L, "lab");

        assertThatThrownBy(() -> service.startTesting(APPLY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未缴费");

        verify(applyMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("已在检验中或已出报告的单不能重复采样")
    void startTestingRejectsFinishedApply() {
        loginAs(8L, "lab");

        BizExamApply testing = new BizExamApply();
        testing.setId(APPLY_ID);
        testing.setStatus(2);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(testing);
        assertThatThrownBy(() -> service.startTesting(APPLY_ID))
                .hasMessageContaining("已在检验中");

        BizExamApply reported = new BizExamApply();
        reported.setId(APPLY_ID);
        reported.setStatus(3);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(reported);
        assertThatThrownBy(() -> service.startTesting(APPLY_ID))
                .hasMessageContaining("已在检验中");
    }

    @Test
    @DisplayName("采样时状态被并发改变返回冲突")
    void startTestingConflict() {
        BizExamApply apply = new BizExamApply();
        apply.setId(APPLY_ID);
        apply.setStatus(1);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply);
        when(applyMapper.transitStatus(anyLong(), any(), any(), any())).thenReturn(0);
        loginAs(8L, "lab");

        assertThatThrownBy(() -> service.startTesting(APPLY_ID))
                .hasMessageContaining("状态已变化");
    }

    /* ---------------- 采样 / 条码 / 明细（本轮新增） ---------------- */

    private BizExamApply apply(int status, String barcode) {
        BizExamApply apply = new BizExamApply();
        apply.setId(APPLY_ID);
        apply.setApplyNo("EXM202610270001");
        apply.setPatientId(PATIENT_ID);
        apply.setStatus(status);
        apply.setBarcodeNo(barcode);
        return apply;
    }

    private BizExamItem item(Long id, String code, String name) {
        BizExamItem item = new BizExamItem();
        item.setId(id);
        item.setApplyId(APPLY_ID);
        item.setItemCode(code);
        item.setItemName(name);
        item.setSort(0);
        return item;
    }

    @Test
    @DisplayName("采样时分配条码号并落库")
    void sampleAssignsBarcode() {
        BizExamApply unpaidBarcode = apply(1, null);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(unpaidBarcode);
        when(applyMapper.transitStatus(eq(APPLY_ID), eq(1), eq(2), any())).thenReturn(1);
        loginAs(8L, "tech");

        String barcode = service.sample(APPLY_ID);

        assertThat(barcode).isEqualTo("SMPEXM202610270001");
        ArgumentCaptor<BizExamApply> captor = ArgumentCaptor.forClass(BizExamApply.class);
        verify(applyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getBarcodeNo()).isEqualTo("SMPEXM202610270001");
    }

    @Test
    @DisplayName("已有条码时重复采样不覆盖（幂等）")
    void sampleKeepsExistingBarcode() {
        BizExamApply withBarcode = apply(1, "SMP-OLD");
        when(applyMapper.selectById(APPLY_ID)).thenReturn(withBarcode);
        when(applyMapper.transitStatus(eq(APPLY_ID), eq(1), eq(2), any())).thenReturn(1);
        loginAs(8L, "tech");

        assertThat(service.sample(APPLY_ID)).isEqualTo("SMP-OLD");
        verify(applyMapper, never()).updateById(any(BizExamApply.class));
    }

    @Test
    @DisplayName("条码首次生成、重复打印保持同一号")
    void barcodeIsIdempotent() {
        BizExamApply target = apply(2, null);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(target);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(7001L, "WBC", "白细胞计数")));
        BizPatient patient = new BizPatient();
        patient.setName("张三");
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient);
        loginAs(8L, "tech");

        ExamBarcodeVO first = service.barcode(APPLY_ID);
        assertThat(first.getBarcodeNo()).isEqualTo("SMPEXM202610270001");
        assertThat(first.getPatientName()).isEqualTo("张三");
        assertThat(first.getItemNames()).containsExactly("白细胞计数");

        // 模拟库中已写入条码
        target.setBarcodeNo("SMPEXM202610270001");
        ExamBarcodeVO second = service.barcode(APPLY_ID);

        assertThat(second.getBarcodeNo()).isEqualTo(first.getBarcodeNo());
        verify(applyMapper, times(1)).updateById(any(BizExamApply.class));
    }

    @Test
    @DisplayName("明细查询：按申请单返回全部项目")
    void itemsOfApply() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2, null));
        when(itemMapper.selectList(any())).thenReturn(
                List.of(item(7001L, "WBC", "白细胞计数"), item(7002L, "CRP", "C反应蛋白")));
        loginAs(8L, "tech");

        List<ExamItemVO> items = service.items(APPLY_ID);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getItemCode()).isEqualTo("WBC");
        assertThat(items.get(1).getItemCode()).isEqualTo("CRP");
    }

    @Test
    @DisplayName("未缴费的检查单不能保存结果")
    void saveItemsRejectsUnpaid() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(0, null));
        loginAs(8L, "tech");

        ExamItemResultRequest req = new ExamItemResultRequest();
        req.setItemId(7001L);
        req.setResultValue("6.2");

        assertThatThrownBy(() -> service.saveItems(APPLY_ID, List.of(req)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未缴费");
        verify(itemMapper, never()).updateById(any(BizExamItem.class));
    }

    @Test
    @DisplayName("已发布报告的明细结果锁定")
    void saveItemsRejectsPublishedReport() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(3, null));
        BizExamReport published = new BizExamReport();
        published.setId(8001L);
        published.setApplyId(APPLY_ID);
        published.setStatus(1);
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(published);
        loginAs(8L, "tech");

        ExamItemResultRequest req = new ExamItemResultRequest();
        req.setItemId(7001L);
        req.setResultValue("9.9");

        assertThatThrownBy(() -> service.saveItems(APPLY_ID, List.of(req)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报告已发布");
    }

    @Test
    @DisplayName("保存结果：只认本单明细，越界直接拒绝")
    void saveItemsRejectsForeignItem() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2, null));
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(null);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(7001L, "WBC", "白细胞计数")));
        loginAs(8L, "tech");

        ExamItemResultRequest foreign = new ExamItemResultRequest();
        foreign.setItemId(9999L);
        foreign.setResultValue("1");

        assertThatThrownBy(() -> service.saveItems(APPLY_ID, List.of(foreign)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该检查单");
        verify(itemMapper, never()).updateById(any(BizExamItem.class));
    }

    @Test
    @DisplayName("保存结果：只改结果字段，不动项目编码与名称")
    void saveItemsUpdatesResultOnly() {
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply(2, null));
        when(reportMapper.selectByApplyId(APPLY_ID)).thenReturn(null);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(7001L, "WBC", "白细胞计数")));
        loginAs(8L, "tech");

        ExamItemResultRequest req = new ExamItemResultRequest();
        req.setItemId(7001L);
        req.setResultValue("6.2");
        req.setRefRange("4-10");
        req.setUnit("10^9/L");
        req.setAbnormalFlag(1);

        service.saveItems(APPLY_ID, List.of(req));

        ArgumentCaptor<BizExamItem> captor = ArgumentCaptor.forClass(BizExamItem.class);
        verify(itemMapper).updateById(captor.capture());
        BizExamItem updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(7001L);
        assertThat(updated.getResultValue()).isEqualTo("6.2");
        assertThat(updated.getRefRange()).isEqualTo("4-10");
        assertThat(updated.getUnit()).isEqualTo("10^9/L");
        assertThat(updated.getAbnormalFlag()).isEqualTo(1);
        // 编码与名称不在更新范围内：改它们是「换了一个项目」，不是「填了一个结果」
        assertThat(updated.getItemCode()).isNull();
        assertThat(updated.getItemName()).isNull();
    }
}
