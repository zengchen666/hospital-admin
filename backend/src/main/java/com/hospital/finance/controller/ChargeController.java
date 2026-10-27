package com.hospital.finance.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.finance.dto.ChargeQuery;
import com.hospital.finance.dto.ExamChargeRequest;
import com.hospital.finance.dto.ChargeRequest;
import com.hospital.finance.dto.ChargeResult;
import com.hospital.finance.dto.ChargeVO;
import com.hospital.finance.service.ChargeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收费接口。
 *
 * <p>{@code finance:charge:add} 收费、{@code finance:query} 查询，
 * 两个权限分开便于「收费员能收钱但查不了全院账」这类岗位配置。</p>
 */
@Validated
@RestController
@RequestMapping("/finance/charge")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    /** 处方收费：建单 + 收款一次完成 */
    @PostMapping("/prescription")
    @PreAuthorize("hasAuthority('finance:charge:add')")
    @AuditLog(module = "收费管理", operation = "处方收费")
    public Result<ChargeResult> chargePrescription(@Valid @RequestBody ChargeRequest request) {
        return Result.ok(chargeService.chargePrescription(request));
    }

    /**
     * 检查检验收费：开单只是「待缴费」，收到钱才推进到「待采样」。
     *
     * <p>未收费的检查单在 {@code /exam/apply/{id}/start} 会被挡回去，
     * 这里是唯一能把它放行的入口。</p>
     */
    @PostMapping("/exam")
    @PreAuthorize("hasAuthority('finance:charge:add')")
    @AuditLog(module = "收费管理", operation = "检查检验收费")
    public Result<ChargeResult> chargeExam(@Valid @RequestBody ExamChargeRequest request) {
        return Result.ok(chargeService.chargeExam(request));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<PageResult<ChargeVO>> page(@Valid ChargeQuery query) {
        return Result.ok(chargeService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<ChargeVO> detail(@PathVariable Long id) {
        return Result.ok(chargeService.detail(id));
    }
}
