package com.hospital.finance.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.finance.dto.TreatItemQuery;
import com.hospital.finance.dto.TreatItemRequest;
import com.hospital.finance.dto.TreatItemVO;
import com.hospital.finance.service.TreatItemService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 诊疗项目价目表。
 *
 * <p>调价走 {@code POST /treat/{id}/price} 而不是复用 PUT：
 * 「维护项目」和「调整价格」是两件后果完全不同的事，
 * 一个是改描述，一个会产生新的价格版本并影响后续所有收费。
 * 合成一个接口，审计日志里就分不清这次到底是改了名字还是改了钱。</p>
 */
@Validated
@RestController
@RequestMapping("/finance/treat")
public class TreatItemController {

    private final TreatItemService treatItemService;

    public TreatItemController(TreatItemService treatItemService) {
        this.treatItemService = treatItemService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<PageResult<TreatItemVO>> page(@Valid TreatItemQuery query) {
        return Result.ok(treatItemService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<TreatItemVO> detail(@PathVariable Long id) {
        return Result.ok(treatItemService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('finance:treat:edit')")
    @AuditLog(module = "收费管理", operation = "新增诊疗项目")
    public Result<String> create(@Valid @RequestBody TreatItemRequest request) {
        return Result.ok(String.valueOf(treatItemService.create(request)));
    }

    /** 维护非价格字段（名称 / 规格 / 单位 / 医保属性），不产生新版本 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:treat:edit')")
    @AuditLog(module = "收费管理", operation = "维护诊疗项目")
    public Result<String> update(@PathVariable Long id, @Valid @RequestBody TreatItemRequest request) {
        return Result.ok(String.valueOf(treatItemService.adjustPrice(id, request)));
    }

    /** 调价：新增一版价格，历史价原样保留 */
    @PostMapping("/{id}/price")
    @PreAuthorize("hasAuthority('finance:treat:edit')")
    @AuditLog(module = "收费管理", operation = "诊疗项目调价")
    public Result<String> adjustPrice(@PathVariable Long id,
                                      @Valid @RequestBody TreatItemRequest request) {
        return Result.ok(String.valueOf(treatItemService.adjustPrice(id, request)));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('finance:treat:edit')")
    @AuditLog(module = "收费管理", operation = "停用诊疗项目")
    public Result<Void> disable(@PathVariable Long id) {
        treatItemService.disable(id);
        return Result.ok();
    }
}
