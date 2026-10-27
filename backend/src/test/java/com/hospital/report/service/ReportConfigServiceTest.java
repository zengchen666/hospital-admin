package com.hospital.report.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.report.dto.DrugStatVO;
import com.hospital.report.dto.IncomeStatVO;
import com.hospital.report.dto.InpatientStatVO;
import com.hospital.report.dto.OutpatientStatVO;
import com.hospital.report.dto.PatientStatVO;
import com.hospital.report.dto.ReportConfigQuery;
import com.hospital.report.dto.ReportConfigRequest;
import com.hospital.report.dto.ReportConfigVO;
import com.hospital.report.dto.ReportQuery;
import com.hospital.report.dto.ReportResultVO;
import com.hospital.report.entity.SysReportConfig;
import com.hospital.report.mapper.SysReportConfigMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自定义报表配置：白名单落库、内置配置保护、执行结果列定义。
 *
 * <p>重点关注<b>内置配置不能被用户改掉</b>：内置那条是系统快照的挂靠点，
 * creator_id 为空。一旦允许改删，快照会挂到用户配置上或整个丢失，
 * 而这类问题在界面上表现为「昨天的报表今天打不开了」，极难定位。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportConfigServiceTest {

    @Mock
    private SysReportConfigMapper configMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ReportService reportService;

    private ReportConfigService service;

    @BeforeEach
    void setUp() {
        service = new ReportConfigService(configMapper, userMapper, reportService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("admin")
                .roleCodes(new LinkedHashSet<>(Set.of("admin")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private ReportConfigRequest request(String name, String type, String dimension) {
        ReportConfigRequest request = new ReportConfigRequest();
        request.setReportName(name);
        request.setReportType(type);
        request.setDimension(dimension);
        request.setChartType("bar");
        return request;
    }

    private SysReportConfig entity(Long id, String type, String dimension, Long creatorId) {
        SysReportConfig entity = new SysReportConfig();
        entity.setId(id);
        entity.setReportName("我的报表");
        entity.setReportType(type);
        entity.setDimension(dimension);
        entity.setChartType("table");
        entity.setCreatorId(creatorId);
        return entity;
    }

    /* ---------------- 落库校验 ---------------- */

    @Test
    @DisplayName("新建配置落库前先过白名单，并记下创建人")
    void createPersistsNormalizedValues() {
        loginAs(7L);

        Long id = service.create(request("  内科收入月报  ", "income", "dept"));

        ArgumentCaptor<SysReportConfig> captor = ArgumentCaptor.forClass(SysReportConfig.class);
        verify(configMapper).insert(captor.capture());
        SysReportConfig saved = captor.getValue();
        assertThat(saved.getReportName()).isEqualTo("内科收入月报");
        assertThat(saved.getReportType()).isEqualTo("income");
        assertThat(saved.getDimension()).isEqualTo("dept");
        assertThat(saved.getChartType()).isEqualTo("bar");
        assertThat(saved.getCreatorId()).isEqualTo(7L);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("数据集非法时 40001，且不落库")
    void createRejectsUnknownType() {
        loginAs(7L);

        assertThatThrownBy(() -> service.create(request("报表", "nosuch", "dept")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表类型非法");

        verify(configMapper, never()).insert(any(SysReportConfig.class));
    }

    @Test
    @DisplayName("数据集与维度不匹配时 40001，且不落库")
    void createRejectsMismatchedDimension() {
        loginAs(7L);

        assertThatThrownBy(() -> service.create(request("报表", "drug", "dept")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持维度 dept");

        verify(configMapper, never()).insert(any(SysReportConfig.class));
    }

    /* ---------------- 内置配置保护 ---------------- */

    @Test
    @DisplayName("内置配置（creator_id 为空）不允许删除")
    void builtinConfigCannotBeDeleted() {
        when(configMapper.selectById(9001L)).thenReturn(entity(9001L, "income", "itemType", null));

        assertThatThrownBy(() -> service.delete(9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统内置报表配置不允许删除");

        verify(configMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("内置配置不允许修改")
    void builtinConfigCannotBeUpdated() {
        when(configMapper.selectById(9001L)).thenReturn(entity(9001L, "income", "itemType", null));

        assertThatThrownBy(() -> service.update(9001L, request("改名", "income", "itemType")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统内置报表配置不允许修改");

        verify(configMapper, never()).updateById(any(SysReportConfig.class));
    }

    @Test
    @DisplayName("用户自建配置可以删除")
    void userConfigCanBeDeleted() {
        when(configMapper.selectById(9002L)).thenReturn(entity(9002L, "income", "dept", 7L));

        service.delete(9002L);

        verify(configMapper).deleteById(9002L);
    }

    @Test
    @DisplayName("配置不存在时报 40400，而不是空指针")
    void missingConfigIsNotFound() {
        when(configMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表配置不存在或已删除");
    }

    /* ---------------- 列表与视图 ---------------- */

    @Test
    @DisplayName("分页结果带创建人姓名与维度中文名")
    @SuppressWarnings("unchecked")
    void pageFillsCreatorAndDimensionName() {
        Page<SysReportConfig> page = new Page<>(1, 10);
        page.setRecords(List.of(entity(9002L, "income", "dept", 7L)));
        page.setTotal(1);
        when(configMapper.selectPage(any(IPage.class), any())).thenReturn(page);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setRealName("张院长");
        when(userMapper.selectByIds(List.of(7L))).thenReturn(List.of(user));

        var result = service.page(new ReportConfigQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        ReportConfigVO vo = result.getList().get(0);
        assertThat(vo.getCreatorName()).isEqualTo("张院长");
        assertThat(vo.getDimensionName()).isEqualTo("科室");
        assertThat(vo.getReportTypeName()).isEqualTo("收入统计");
        assertThat(vo.getBuiltin()).isFalse();
    }

    @Test
    @DisplayName("内置配置标记为 builtin，前端据此隐藏删除按钮")
    void builtinFlagIsExposed() {
        when(configMapper.selectById(9001L)).thenReturn(entity(9001L, "income", "itemType", null));

        assertThat(service.detail(9001L).getBuiltin()).isTrue();
    }

    /* ---------------- 执行 ---------------- */

    @Test
    @DisplayName("门诊 + 科室：列含占比，行按科室输出")
    void executeOutpatientByDept() {
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        OutpatientStatVO row = new OutpatientStatVO();
        row.setDeptName("内科");
        row.setRegCount(30L);
        row.setPatientCount(20L);
        row.setRegFeeAmount(new BigDecimal("300.00"));
        row.setRatio(new BigDecimal("60.00"));
        when(reportService.outpatientByDept(any())).thenReturn(List.of(row));

        ReportResultVO result = service.execute("outpatient", "dept", new ReportQuery());

        assertThat(result.getColumns()).extracting("title")
                .containsExactly("科室", "挂号人次", "就诊人数", "挂号费收入", "占比(%)");
        assertThat(result.getRows()).hasSize(1);
        Map<String, Object> firstRow = result.getRows().get(0);
        assertThat(firstRow.get("dimension")).isEqualTo("内科");
        assertThat(firstRow.get("regCount")).isEqualTo(30L);
        assertThat(firstRow.get("ratio")).isEqualTo(new BigDecimal("60.00"));
        assertThat(result.getRangeDesc()).isEqualTo("2026-01-01 ~ 2026-01-31");
    }

    @Test
    @DisplayName("门诊 + 日期：趋势视图不带占比（按天算占比没有意义）")
    void executeOutpatientByDate() {
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        OutpatientStatVO row = new OutpatientStatVO();
        row.setStatDate(LocalDate.of(2026, 1, 5));
        row.setRegCount(3L);
        when(reportService.outpatientTrend(any())).thenReturn(List.of(row));

        ReportResultVO result = service.execute("outpatient", "date", new ReportQuery());

        assertThat(result.getColumns()).extracting("title").doesNotContain("占比(%)");
        assertThat(result.getRows().get(0).get("dimension")).isEqualTo("2026-01-05");
    }

    @Test
    @DisplayName("收入 + 医生：列换成接诊人次与开方金额")
    void executeIncomeByDoctor() {
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        when(reportService.income(org.mockito.ArgumentMatchers.eq("doctor"), any()))
                .thenReturn(List.of(IncomeStatVO.builder()
                        .dimensionName("张医生")
                        .amount(BigDecimal.ZERO)
                        .visitCount(12L)
                        .prescAmount(new BigDecimal("480.00"))
                        .build()));

        ReportResultVO result = service.execute("income", "doctor", new ReportQuery());

        assertThat(result.getColumns()).extracting("title")
                .containsExactly("医生", "收入金额", "接诊人次", "开方金额");
        assertThat(result.getRows().get(0).get("visitCount")).isEqualTo(12L);
    }

    @Test
    @DisplayName("住院概况只有一行，列是汇总指标")
    void executeInpatientSummary() {
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        InpatientStatVO vo = new InpatientStatVO();
        vo.setInHospitalCount(8L);
        vo.setBedUsageRate(new BigDecimal("66.67"));
        when(reportService.inpatient(any())).thenReturn(vo);

        ReportResultVO result = service.execute("inpatient", "summary", new ReportQuery());

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).get("bedUsageRate")).isEqualTo(new BigDecimal("66.67"));
        assertThat(result.getColumns()).extracting("title").contains("床位使用率(%)");
    }

    @Test
    @DisplayName("药品排行带名次列")
    void executeDrugRanking() {
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        DrugStatVO row = new DrugStatVO();
        row.setRank(1);
        row.setDrugName("感冒灵颗粒");
        row.setQuantity(20L);
        row.setAmount(new BigDecimal("240.00"));
        when(reportService.drugRanking(any())).thenReturn(List.of(row));

        ReportResultVO result = service.execute("drug", "drug", new ReportQuery());

        assertThat(result.getColumns()).extracting("title")
                .containsExactly("排名", "药品名称", "规格", "消耗数量", "销售金额");
        assertThat(result.getRows().get(0).get("drugName")).isEqualTo("感冒灵颗粒");
    }

    @Test
    @DisplayName("患者分析按年龄段分组")
    void executePatientByAge() {
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        when(reportService.patient(org.mockito.ArgumentMatchers.eq("age"), any()))
                .thenReturn(List.of(PatientStatVO.builder()
                        .displayName("40-59岁")
                        .patientCount(45L)
                        .build()));

        ReportResultVO result = service.execute("patient", "age", new ReportQuery());

        assertThat(result.getColumns()).extracting("title").containsExactly("分组", "人数");
        assertThat(result.getRows().get(0).get("patientCount")).isEqualTo(45L);
    }

    @Test
    @DisplayName("按配置执行时取配置里存的维度")
    void executeByConfigUsesSavedDimension() {
        when(configMapper.selectById(9002L)).thenReturn(entity(9002L, "patient", "gender", 7L));
        when(reportService.describeRange(any())).thenReturn("2026-01-01 ~ 2026-01-31");
        when(reportService.patient(org.mockito.ArgumentMatchers.eq("gender"), any()))
                .thenReturn(List.of(PatientStatVO.builder().displayName("男").patientCount(9L).build()));

        ReportResultVO result = service.execute(9002L, new ReportQuery());

        assertThat(result.getRows().get(0).get("displayName")).isEqualTo("男");
    }

    @Test
    @DisplayName("药品统计配科室维度时 40001（矩阵之外的组合）")
    void executeRejectsMismatchedDimension() {
        assertThatThrownBy(() -> service.execute("drug", "dept", new ReportQuery()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持维度 dept");

        verify(reportService, never()).drugRanking(any());
    }
}
