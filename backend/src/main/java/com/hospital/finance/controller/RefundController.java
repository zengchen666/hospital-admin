package com.hospital.finance.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.finance.dto.RefundQuery;
import com.hospital.finance.dto.RefundRequest;
import com.hospital.finance.dto.RefundVO;
import com.hospital.finance.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

/**
 * 退费接口。
 *
 * <p>申请与审核分成两个权限码（{@code finance:refund:apply} /
 * {@code finance:refund:audit}），就是为了能在岗位配置上把它们拆给两个人。
 * 退费和收费如果是同一个人，长年累月一定出问题。</p>
 */
@Validated
@RestController
@RequestMapping("/finance/refund")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('finance:refund:apply')")
    @AuditLog(module = "收费管理", operation = "申请退费")
    public Result<String> apply(@Valid @RequestBody RefundRequest request) {
        return Result.ok(String.valueOf(refundService.apply(request)));
    }

    /** 审核通过：申请人不能审自己的单（admin 豁免） */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('finance:refund:audit')")
    @AuditLog(module = "收费管理", operation = "退费审核通过")
    public Result<Void> approve(@PathVariable Long id) {
        refundService.approve(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('finance:refund:audit')")
    @AuditLog(module = "收费管理", operation = "退费驳回")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestParam(required = false) @Size(max = 255) String reason) {
        refundService.reject(id, reason);
        return Result.ok();
    }

    /** 执行退款：回冲明细 + 按原批号退药回库 */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('finance:refund:audit')")
    @AuditLog(module = "收费管理", operation = "执行退款")
    public Result<Void> complete(@PathVariable Long id) {
        refundService.complete(id);
        return Result.ok();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<PageResult<RefundVO>> page(@Valid RefundQuery query) {
        return Result.ok(refundService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<RefundVO> detail(@PathVariable Long id) {
        return Result.ok(refundService.detail(id));
    }
}
