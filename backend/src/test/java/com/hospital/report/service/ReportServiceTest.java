package com.hospital.report.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.report.dto.DrugStatVO;
import com.hospital.report.dto.IncomeStatVO;
import com.hospital.report.dto.InpatientStatVO;
import com.hospital.report.dto.OutpatientStatVO;
import com.hospital.report.dto.PatientStatVO;
import com.hospital.report.dto.ReportQuery;
import com.hospital.report.entity.SysReportConfig;
import com.hospital.report.entity.SysReportData;
import com.hospital.report.mapper.ReportStatMapper;
import com.hospital.report.mapper.SysReportConfigMapper;
import com.hospital.report.mapper.SysReportDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统计报表服务：日期防线、维度白名单、口径换算、快照幂等。
 *
 * <p>这里最该守住的不是「算得对」，而是<b>不允许出现一次不受控的大查询</b>：
 * 日期范围必须收敛、维度必须白名单。算错了能改，把库查死了全院一起停。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportStatMapper statMapper;
    @Mock
    private SysReportConfigMapper configMapper;
    @Mock
    private SysReportDataMapper dataMapper;
    @Mock
    private ObjectMapper objectMapper;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(statMapper, configMapper, dataMapper, objectMapper);
    }

    private ReportQuery query(LocalDate start, LocalDate end) {
        ReportQuery query = new ReportQuery();
        query.setStartTime(start);
        query.setEndTime(end);
        return query;
    }

    private OutpatientStatVO outpatientRow(String deptName, long regCount) {
        OutpatientStatVO row = new OutpatientStatVO();
        row.setDeptName(deptName);
        row.setRegCount(regCount);
        row.setPatientCount(regCount);
        return row;
    }

    /* ---------------- 日期防线 ---------------- */

    @Test
    @DisplayName("不传日期时默认近 30 天，而不是全表扫描")
    void defaultRangeIsThirtyDays() {
        when(statMapper.outpatientByDept(any(), any(), any())).thenReturn(List.of());

        service.outpatientByDept(new ReportQuery());

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statMapper).outpatientByDept(startCaptor.capture(), endCaptor.capture(), eq(null));

        LocalDate today = LocalDate.now();
        assertThat(startCaptor.getValue().toLocalDate()).isEqualTo(today.minusDays(29));
        // 上界用次日零点，保证结束日当天 23:59:59 也算进来
        assertThat(endCaptor.getValue().toLocalDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    @DisplayName("查询区间超过 366 天直接拒绝（40001）")
    void rangeOverLimitIsRejected() {
        LocalDate start = LocalDate.now().minusDays(400);
        ReportQuery query = query(start, LocalDate.now());

        assertThatThrownBy(() -> service.outpatientByDept(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 366 天");

        verify(statMapper, never()).outpatientByDept(any(), any(), any());
    }

    @Test
    @DisplayName("开始日期晚于结束日期直接拒绝（40001）")
    void startAfterEndIsRejected() {
        ReportQuery query = query(LocalDate.now(), LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.outpatientByDept(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }

    @Test
    @DisplayName("区间恰好 366 天放行")
    void maxRangeIsAllowed() {
        when(statMapper.outpatientByDept(any(), any(), any())).thenReturn(List.of());
        ReportQuery query = query(LocalDate.now().minusDays(365), LocalDate.now());

        service.outpatientByDept(query);

        verify(statMapper).outpatientByDept(any(), any(), eq(null));
    }

    /* ---------------- 区间说明 ---------------- */

    @Test
    @DisplayName("区间说明回显使用者填的结束日，而不是次日零点")
    void describeRangeUsesInclusiveEndDate() {
        ReportQuery query = query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(service.describeRange(query)).isEqualTo("2026-01-01 ~ 2026-01-31");
    }

    @Test
    @DisplayName("不传日期时区间说明同样给得出（默认近 30 天）")
    void describeRangeFallsBackToDefault() {
        String desc = service.describeRange(new ReportQuery());

        LocalDate today = LocalDate.now();
        assertThat(desc).isEqualTo(today.minusDays(29) + " ~ " + today);
    }

    /* ---------------- 报表类型与维度白名单 ---------------- */

    @Test
    @DisplayName("非法报表类型直接 40001，不让字符串流进 SQL")
    void invalidReportTypeIsRejected() {
        assertThatThrownBy(() -> service.generateSnapshot("nosuchtype", LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表类型非法");

        assertThatThrownBy(() -> service.getSnapshot("'; DROP TABLE--", LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表类型非法");
    }

    @Test
    @DisplayName("非法统计维度直接 40001")
    void invalidDimensionIsRejected() {
        assertThatThrownBy(() -> service.income("dropTable", new ReportQuery()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计维度非法");

        assertThatThrownBy(() -> service.patient("union select", new ReportQuery()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计维度非法");
    }

    @Test
    @DisplayName("维度为空时取默认值而不是报错")
    void blankDimensionFallsBackToDefault() {
        when(statMapper.incomeByItemType(any(), any())).thenReturn(List.of());
        when(statMapper.patientAgeDistribution()).thenReturn(List.of());

        service.income(null, new ReportQuery());
        service.patient("  ", new ReportQuery());

        verify(statMapper).incomeByItemType(any(), any());
        verify(statMapper).patientAgeDistribution();
    }

    /* ---------------- 口径换算 ---------------- */

    @Test
    @DisplayName("门诊占比按人次总和计算")
    void ratioIsCalculatedAgainstTotal() {
        when(statMapper.outpatientByDept(any(), any(), any()))
                .thenReturn(List.of(outpatientRow("内科", 3L), outpatientRow("外科", 1L)));

        List<OutpatientStatVO> rows = service.outpatientByDept(new ReportQuery());

        assertThat(rows.get(0).getRatio()).isEqualByComparingTo("75.00");
        assertThat(rows.get(1).getRatio()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("全院人次为 0 时占比为 0，不能除零抛异常")
    void ratioIsZeroWhenNoData() {
        when(statMapper.outpatientByDept(any(), any(), any()))
                .thenReturn(List.of(outpatientRow("内科", 0L)));

        List<OutpatientStatVO> rows = service.outpatientByDept(new ReportQuery());

        assertThat(rows.get(0).getRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("床位使用率：分母排除维修床后的可用床位")
    void bedUsageRateUsesAvailableBeds() {
        InpatientStatVO vo = new InpatientStatVO();
        vo.setOccupiedBeds(3L);
        vo.setAvailableBeds(4L);
        when(statMapper.inpatientSummary(any(), any())).thenReturn(vo);

        InpatientStatVO result = service.inpatient(new ReportQuery());

        assertThat(result.getBedUsageRate()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("可用床位为 0 时使用率按 0 处理，不能除零")
    void bedUsageRateIsZeroWhenNoAvailableBed() {
        InpatientStatVO vo = new InpatientStatVO();
        vo.setOccupiedBeds(0L);
        vo.setAvailableBeds(0L);
        when(statMapper.inpatientSummary(any(), any())).thenReturn(vo);

        assertThat(service.inpatient(new ReportQuery()).getBedUsageRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("药品排行按金额降序补名次")
    void drugRankIsFilledByOrder() {
        DrugStatVO first = new DrugStatVO();
        first.setDrugName("感冒灵颗粒");
        first.setAmount(new BigDecimal("40.00"));
        DrugStatVO second = new DrugStatVO();
        second.setDrugName("阿莫西林胶囊");
        second.setAmount(new BigDecimal("36.00"));
        when(statMapper.drugRanking(any(), any(), anyInt())).thenReturn(List.of(first, second));

        List<DrugStatVO> rows = service.drugRanking(new ReportQuery());

        assertThat(rows.get(0).getRank()).isEqualTo(1);
        assertThat(rows.get(1).getRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("topN 传给 SQL 做 LIMIT，不在内存里截断")
    void topNIsPushedDownToSql() {
        when(statMapper.drugRanking(any(), any(), anyInt())).thenReturn(List.of());
        ReportQuery query = new ReportQuery();
        query.setTopN(10);

        service.drugRanking(query);

        verify(statMapper).drugRanking(any(), any(), eq(10));
    }

    /* ---------------- 预聚合快照 ---------------- */

    @Test
    @DisplayName("不能为未来日期生成快照")
    void futureSnapshotIsRejected() {
        assertThatThrownBy(() -> service.generateSnapshot("inpatient", LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为未来日期生成快照");

        verify(dataMapper, never()).upsert(any(SysReportData.class));
    }

    @Test
    @DisplayName("生成快照走 upsert，重复生成是覆盖而非新增")
    void snapshotUsesUpsert() throws Exception {
        InpatientStatVO vo = new InpatientStatVO();
        vo.setInHospitalCount(5L);
        when(statMapper.inpatientSummary(any(), any())).thenReturn(vo);
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"inHospitalCount\":5}]");
        SysReportConfig config = new SysReportConfig();
        config.setId(9001L);
        when(configMapper.selectByType("inpatient")).thenReturn(config);

        Long id = service.generateSnapshot("inpatient", LocalDate.now().minusDays(1));

        assertThat(id).isNotNull();
        ArgumentCaptor<SysReportData> captor = ArgumentCaptor.forClass(SysReportData.class);
        verify(dataMapper).upsert(captor.capture());
        SysReportData saved = captor.getValue();
        assertThat(saved.getReportId()).isEqualTo(9001L);
        assertThat(saved.getStatDate()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(saved.getDataJson()).isEqualTo("[{\"inHospitalCount\":5}]");
        // 自定义 @Insert 不会触发 MP 的 ID 填充，这里必须已经手动赋好
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("报表配置不存在时自动建一条，而不是报空指针")
    void configIsCreatedOnFirstSnapshot() throws Exception {
        when(statMapper.incomeByItemType(any(), any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(configMapper.selectByType("income")).thenReturn(null);

        service.generateSnapshot("income", LocalDate.now().minusDays(1));

        ArgumentCaptor<SysReportConfig> captor = ArgumentCaptor.forClass(SysReportConfig.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getReportType()).isEqualTo("income");
        assertThat(captor.getValue().getReportName()).isEqualTo("收入统计");
        verify(dataMapper).upsert(any(SysReportData.class));
    }

    @Test
    @DisplayName("未生成快照时读取返回 null，由 Controller 转成 data=null")
    void snapshotIsNullWhenMissing() {
        SysReportConfig config = new SysReportConfig();
        config.setId(9001L);
        when(configMapper.selectByType("drug")).thenReturn(config);
        when(dataMapper.selectByDate(eq(9001L), any())).thenReturn(null);

        assertThat(service.getSnapshot("drug", LocalDate.now().minusDays(1))).isNull();
    }

    /* ---------------- 各维度路由 ---------------- */

    @Test
    @DisplayName("收入三个维度分别命中不同 SQL")
    void incomeRoutesToDifferentSql() {
        when(statMapper.incomeByItemType(any(), any())).thenReturn(List.of());
        when(statMapper.incomeByDept(any(), any())).thenReturn(List.of());
        when(statMapper.doctorPerformance(any(), any(), anyInt())).thenReturn(List.of());

        service.income("itemType", new ReportQuery());
        service.income("dept", new ReportQuery());
        service.income("doctor", new ReportQuery());

        verify(statMapper).incomeByItemType(any(), any());
        verify(statMapper).incomeByDept(any(), any());
        verify(statMapper).doctorPerformance(any(), any(), anyInt());
    }

    @Test
    @DisplayName("患者分析四个维度分别命中不同 SQL")
    void patientRoutesToDifferentSql() {
        when(statMapper.patientAgeDistribution()).thenReturn(List.of());
        when(statMapper.patientGenderDistribution()).thenReturn(List.of());
        when(statMapper.diseaseRanking(any(), any(), anyInt())).thenReturn(List.of());
        when(statMapper.newPatientTrend(any(), any())).thenReturn(List.of());

        service.patient("age", new ReportQuery());
        service.patient("gender", new ReportQuery());
        service.patient("disease", new ReportQuery());
        service.patient("new", new ReportQuery());

        verify(statMapper).patientAgeDistribution();
        verify(statMapper).patientGenderDistribution();
        verify(statMapper).diseaseRanking(any(), any(), anyInt());
        verify(statMapper).newPatientTrend(any(), any());
    }

    @Test
    @DisplayName("年龄/性别是全量画像，不受时间区间影响")
    void patientProfileIgnoresDateRange() {
        when(statMapper.patientAgeDistribution()).thenReturn(List.of(
                PatientStatVO.builder().dimension("AGE").dimensionName("0-17").patientCount(1L).build()));
        // 时间区间明显在去年，年龄分布仍应照常返回
        ReportQuery query = query(LocalDate.now().minusYears(1).minusDays(10), LocalDate.now().minusYears(1));

        List<PatientStatVO> rows = service.patient("age", query);

        assertThat(rows).hasSize(1);
        verify(statMapper).patientAgeDistribution();
    }

    @Test
    @DisplayName("医生绩效返回接诊量与开方金额")
    void doctorPerformanceReturnsBothMetrics() {
        when(statMapper.doctorPerformance(any(), any(), anyInt())).thenReturn(List.of(
                IncomeStatVO.builder()
                        .dimension("DOCTOR")
                        .dimensionName("张医生")
                        .visitCount(3L)
                        .prescAmount(new BigDecimal("121.00"))
                        .build()));

        List<IncomeStatVO> rows = service.income("doctor", new ReportQuery());

        assertThat(rows.get(0).getVisitCount()).isEqualTo(3L);
        assertThat(rows.get(0).getPrescAmount()).isEqualByComparingTo("121.00");
    }
}
