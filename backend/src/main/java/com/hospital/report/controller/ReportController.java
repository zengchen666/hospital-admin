package com.hospital.report.controller;

import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.report.dto.DrugStatVO;
import com.hospital.report.dto.IncomeStatVO;
import com.hospital.report.dto.InpatientStatVO;
import com.hospital.report.dto.OutpatientStatVO;
import com.hospital.report.dto.PatientStatVO;
import com.hospital.report.dto.ReportQuery;
import com.hospital.report.dto.ReportResultVO;
import com.hospital.report.service.ReportConfigService;
import com.hospital.report.service.ReportExportService;
import com.hospital.report.service.ReportService;
import com.hospital.report.service.ReportType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计报表接口，对应需求文档模块十。
 *
 * <p>报表能看到全院经营数据，权限单独拆开（{@code report:query}），
 * 不挂在业务模块权限下 —— 否则一个挂号员就能看到全院收入。
 * 导出与生成快照再单独授权。</p>
 */
@Validated
@RestController
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;
    private final ReportConfigService configService;
    private final ReportExportService exportService;

    public ReportController(ReportService reportService,
                            ReportConfigService configService,
                            ReportExportService exportService) {
        this.reportService = reportService;
        this.configService = configService;
        this.exportService = exportService;
    }

    /** 门诊量科室分布 */
    @GetMapping("/outpatient")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<List<OutpatientStatVO>> outpatientByDept(@Valid ReportQuery query) {
        return Result.ok(reportService.outpatientByDept(query));
    }

    /** 门诊量按日趋势 */
    @GetMapping("/outpatient/trend")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<List<OutpatientStatVO>> outpatientTrend(@Valid ReportQuery query) {
        return Result.ok(reportService.outpatientTrend(query));
    }

    /** 住院概况：在院人数、床位使用率、平均住院日 */
    @GetMapping("/inpatient")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<InpatientStatVO> inpatient(@Valid ReportQuery query) {
        return Result.ok(reportService.inpatient(query));
    }

    /**
     * 收入统计。
     *
     * @param dimension itemType 收入构成 / dept 科室收入 / doctor 医生绩效
     */
    @GetMapping("/income")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<List<IncomeStatVO>> income(@RequestParam(defaultValue = "itemType") String dimension,
                                             @Valid ReportQuery query) {
        return Result.ok(reportService.income(dimension, query));
    }

    /** 药品销售排行 */
    @GetMapping("/drug")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<List<DrugStatVO>> drugRanking(@Valid ReportQuery query) {
        return Result.ok(reportService.drugRanking(query));
    }

    /**
     * 患者分析。
     *
     * @param dimension age 年龄分布 / gender 性别 / disease 病种 / new 建档趋势
     */
    @GetMapping("/patient")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<List<PatientStatVO>> patient(@RequestParam(defaultValue = "age") String dimension,
                                               @Valid ReportQuery query) {
        return Result.ok(reportService.patient(dimension, query));
    }

    /* ---------------- 预聚合快照 ---------------- */

    /**
     * 生成某天的预聚合快照（幂等：同一天重复生成是覆盖）。
     *
     * <p>生成是写操作，且会给库带来一次聚合压力，因此单独授权
     * {@code report:generate} 并留审计。</p>
     */
    @PostMapping("/{type}/snapshot")
    @PreAuthorize("hasAuthority('report:generate')")
    @AuditLog(module = "统计报表", operation = "生成报表快照")
    public Result<String> generateSnapshot(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(String.valueOf(reportService.generateSnapshot(type, date)));
    }

    /** 读取快照；未生成时 data 为 null */
    @GetMapping("/{type}/snapshot")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<String> getSnapshot(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(reportService.getSnapshot(type, date));
    }

    /* ---------------- 导出 ---------------- */

    /**
     * 导出报表为 Excel / PDF。
     *
     * <p>导出单独授权 {@code report:export}：<b>能看不等于能带走</b>。
     * 屏幕上看全院收入是一次性行为，导成文件之后它会离开系统，
     * 出现在邮件附件、微信群和汇报材料里，再也无法回收。</p>
     *
     * <p>这里不走 {@link com.hospital.common.api.Result} 包装：
     * 文件流本身就是响应体，再套一层 JSON 会让前端拿不到二进制。</p>
     *
     * @param format excel / pdf
     * @param dimension 统计维度，为空取该数据集的默认维度
     */
    @GetMapping("/{type}/export")
    @PreAuthorize("hasAuthority('report:export')")
    @AuditLog(module = "统计报表", operation = "导出报表文件")
    public void export(@PathVariable String type,
                       @RequestParam(defaultValue = "excel") String format,
                       @RequestParam(required = false) String dimension,
                       @Valid ReportQuery query,
                       HttpServletResponse response) throws IOException {
        String normalized = ReportExportService.normalizeFormat(format);
        ReportResultVO result = configService.execute(type, dimension, query);

        String title = ReportType.of(type).getName()
                + (dimension == null || dimension.isBlank() ? "" : "（" + dimension + "）");
        byte[] bytes = "pdf".equals(normalized)
                ? exportService.exportPdf(title, result)
                : exportService.exportExcel(title, result);

        response.setContentType("pdf".equals(normalized)
                ? "application/pdf" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", exportService.contentDisposition(title, normalized));
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
