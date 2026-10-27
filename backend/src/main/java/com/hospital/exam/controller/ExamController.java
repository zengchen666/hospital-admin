package com.hospital.exam.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.exam.dto.CriticalHandleRequest;
import com.hospital.exam.dto.ExamItemVO;
import com.hospital.exam.dto.ExamItemResultRequest;
import com.hospital.exam.dto.SampleRequest;
import com.hospital.exam.dto.ExamBarcodeVO;
import com.hospital.exam.dto.ExamApplyQuery;
import com.hospital.exam.dto.ExamApplyRequest;
import com.hospital.exam.dto.ExamApplyVO;
import com.hospital.exam.dto.ExamReportQuery;
import com.hospital.exam.dto.ExamReportVO;
import com.hospital.exam.dto.ExamResultRequest;
import com.hospital.exam.service.ExamApplyService;
import com.hospital.exam.service.ExamReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * 检查检验接口，对应文档 8.2。
 *
 * <p>权限按岗位拆开：医生 {@code exam:apply:add}、技师 {@code exam:report:entry}、
 * 审核医师 {@code exam:report:audit}、临床处理危急值 {@code exam:critical:handle}。</p>
 */
@Validated
@RestController
@RequestMapping("/exam")
public class ExamController {

    private final ExamApplyService applyService;
    private final ExamReportService reportService;

    public ExamController(ExamApplyService applyService, ExamReportService reportService) {
        this.applyService = applyService;
        this.reportService = reportService;
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('exam:apply:add')")
    @AuditLog(module = "检查检验", operation = "开检查单")
    public Result<String> createApply(@Valid @RequestBody ExamApplyRequest request) {
        return Result.ok(String.valueOf(applyService.create(request)));
    }

    @GetMapping("/apply/page")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<PageResult<ExamApplyVO>> applyPage(@Valid ExamApplyQuery query) {
        return Result.ok(applyService.page(query));
    }

    @GetMapping("/apply/{id}")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<ExamApplyVO> applyDetail(@PathVariable Long id) {
        return Result.ok(applyService.detail(id));
    }

    /** 采样 / 开始检查 */
    @PutMapping("/apply/{id}/start")
    @PreAuthorize("hasAuthority('exam:report:entry')")
    @AuditLog(module = "检查检验", operation = "采样/开始检查")
    public Result<Void> startTesting(@PathVariable Long id) {
        applyService.startTesting(id);
        return Result.ok();
    }

    /** 录入报告（草稿可反复覆盖，发布后锁定） */
    @PostMapping("/report")
    @PreAuthorize("hasAuthority('exam:report:entry')")
    @AuditLog(module = "检查检验", operation = "录入检查报告")
    public Result<String> saveReport(@Valid @RequestBody ExamResultRequest request) {
        return Result.ok(String.valueOf(reportService.save(request)));
    }

    /** 按申请单查报告；未出报告时返回 {@code data=null} */
    @GetMapping("/report/by-apply/{applyId}")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<ExamReportVO> reportByApply(
            @PathVariable @NotNull(message = "申请单 ID 不能为空") Long applyId) {
        return Result.ok(reportService.getByApply(applyId));
    }

    @PutMapping("/report/{id}/audit")
    @PreAuthorize("hasAuthority('exam:report:audit')")
    @AuditLog(module = "检查检验", operation = "审核发布报告")
    public Result<Void> auditReport(@PathVariable Long id) {
        reportService.audit(id);
        return Result.ok();
    }

    /**
     * 报告详情。
     *
     * <p>与 {@code /report/by-apply/{applyId}} 的区别：那个按申请单查（未出报告返回 null），
     * 这个按报告 ID 查（不存在直接 404），用于从列表点进详情。</p>
     */
    @GetMapping("/report/{id}")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<ExamReportVO> reportDetail(@PathVariable Long id) {
        return Result.ok(reportService.detail(id));
    }

    /**
     * 报告发布。
     *
     * <p>与 {@code /report/{id}/audit} 是同一个动作：本系统报告状态机只有「草稿 → 已发布」，
     * 审核通过与发布是一步。文档里拆成两个接口是为了让前端语义清晰，
     * 后端不额外搞一个「已审核未发布」的中间态 —— 那需要再多一次人工动作，
     * 而实际上没有人会在审核通过后把报告压着不发。</p>
     */
    @PutMapping("/report/{id}/publish")
    @PreAuthorize("hasAuthority('exam:report:audit')")
    @AuditLog(module = "检查检验", operation = "发布检查报告")
    public Result<Void> publishReport(@PathVariable Long id) {
        reportService.audit(id);
        return Result.ok();
    }

    /**
     * 危急值处理登记。
     *
     * <p>处理措施必填：危急值闭环的核心是「做了什么」，
     * 只勾一个「已处理」等于什么都没留下，事后追责时无从谈起。</p>
     */
    @PostMapping("/critical/handle")
    @PreAuthorize("hasAuthority('exam:critical:handle')")
    @AuditLog(module = "检查检验", operation = "危急值处理")
    public Result<Void> handleCritical(@Valid @RequestBody CriticalHandleRequest request) {
        reportService.handleCritical(request.getReportId(), request.getRemark());
        return Result.ok();
    }

    /** 申请单下的项目明细（含结果），结果录入页用它做表单骨架 */
    @GetMapping("/item/{applyId}")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<List<ExamItemVO>> items(@PathVariable Long applyId) {
        return Result.ok(applyService.items(applyId));
    }

    /** 批量保存项目结果（技师填数值，不动报告主表） */
    @PutMapping("/item/{applyId}")
    @PreAuthorize("hasAuthority('exam:report:entry')")
    @AuditLog(module = "检查检验", operation = "录入项目结果")
    public Result<Void> saveItems(@PathVariable Long applyId,
                                  @Valid @RequestBody List<ExamItemResultRequest> items) {
        applyService.saveItems(applyId, items);
        return Result.ok();
    }

    /**
     * 样本采集：待采样 → 检验中，并分配条码号。
     *
     * <p>未缴费的单不能采样 —— 这是收费与检查之间唯一的一道闸门。</p>
     */
    @PostMapping("/sample")
    @PreAuthorize("hasAuthority('exam:report:entry')")
    @AuditLog(module = "检查检验", operation = "样本采集")
    public Result<String> sample(@Valid @RequestBody SampleRequest request) {
        return Result.ok(applyService.sample(request.getApplyId()));
    }

    /** 样本条码：首次调用生成，重复调用返回同一个号 */
    @PostMapping("/sample/{id}/barcode")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<ExamBarcodeVO> barcode(@PathVariable Long id) {
        return Result.ok(applyService.barcode(id));
    }

    /**
     * 报告分页查询。
     *
     * <p>与 {@code /apply/page} 的分工：那个看申请单（开了什么检查、到哪一步了），
     * 这个看报告（结果是什么、发布没有、危急值处理了没有）。</p>
     */
    @GetMapping("/report/page")
    @PreAuthorize("hasAuthority('exam:query')")
    public Result<PageResult<ExamReportVO>> reportPage(@Valid ExamReportQuery query) {
        return Result.ok(reportService.page(query));
    }

    /**
     * 危急值待处理列表。
     *
     * <p>只返回已发布且标记危急值的报告 —— 草稿状态的危急值还没经审核，不该惊动临床。</p>
     */
    @GetMapping("/report/critical")
    @PreAuthorize("hasAuthority('exam:critical:handle')")
    public Result<PageResult<ExamReportVO>> criticalReports(@Valid ExamApplyQuery query) {
        return Result.ok(reportService.pageCritical(query));
    }
}
