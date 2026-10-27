package com.hospital.report.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.report.dto.ReportConfigQuery;
import com.hospital.report.dto.ReportConfigRequest;
import com.hospital.report.dto.ReportConfigVO;
import com.hospital.report.dto.ReportQuery;
import com.hospital.report.dto.ReportResultVO;
import com.hospital.report.service.ReportConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自定义报表配置接口（需求文档模块十「自定义报表」）。
 *
 * <p>权限分两档：<b>看</b>用 {@code report:query}，<b>改</b>用 {@code report:config}。
 * 能看全院收入的人很多（院领导、科室主任、财务），
 * 但能改所有人报表配置的人应该很少 —— 改配置等于改别人看到的数字口径。</p>
 */
@Validated
@RestController
@RequestMapping("/report/config")
public class ReportConfigController {

    private final ReportConfigService configService;

    public ReportConfigController(ReportConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<PageResult<ReportConfigVO>> page(@Valid ReportConfigQuery query) {
        return Result.ok(configService.page(query));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<List<ReportConfigVO>> list(String reportType) {
        return Result.ok(configService.list(reportType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<ReportConfigVO> detail(@PathVariable Long id) {
        return Result.ok(configService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('report:config')")
    @AuditLog(module = "统计报表", operation = "新建自定义报表")
    public Result<Long> create(@Valid @RequestBody ReportConfigRequest request) {
        return Result.ok(configService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('report:config')")
    @AuditLog(module = "统计报表", operation = "修改自定义报表")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ReportConfigRequest request) {
        configService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('report:config')")
    @AuditLog(module = "统计报表", operation = "删除自定义报表")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.ok();
    }

    /**
     * 预览：不落库，直接按「数据集 + 维度」跑一次。
     *
     * <p>拖拽画布需要边拖边看结果，要求先保存再预览会把画布逼成
     * 「保存一堆半成品配置」。</p>
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<ReportResultVO> preview(@RequestParam String reportType,
                                          @RequestParam String dimension,
                                          @Valid ReportQuery query) {
        return Result.ok(configService.execute(reportType, dimension, query));
    }

    /** 按已保存的配置执行 */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<ReportResultVO> execute(@PathVariable Long id, @Valid ReportQuery query) {
        return Result.ok(configService.execute(id, query));
    }
}
