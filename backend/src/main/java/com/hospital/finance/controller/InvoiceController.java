package com.hospital.finance.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.finance.dto.InvoicePrintRequest;
import com.hospital.finance.dto.InvoiceQuery;
import com.hospital.finance.dto.InvoiceVO;
import com.hospital.finance.service.InvoiceService;
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
 * 发票接口。
 *
 * <p>开票放行给收费员（{@code finance:charge:add} 的人本来就能收钱），
 * 作废单独收口到 {@code finance:invoice:void} ——
 * 作废是冲抵收入的动作，不该和开票是同一个权限。</p>
 */
@Validated
@RestController
@RequestMapping("/finance/invoice")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/print")
    @PreAuthorize("hasAuthority('finance:charge:add')")
    @AuditLog(module = "收费管理", operation = "开票")
    public Result<String> print(@Valid @RequestBody InvoicePrintRequest request) {
        return Result.ok(String.valueOf(invoiceService.print(request.getChargeId(), request.getInvoiceType())));
    }

    @PutMapping("/{id}/void")
    @PreAuthorize("hasAuthority('finance:invoice:void')")
    @AuditLog(module = "收费管理", operation = "发票作废")
    public Result<Void> voidInvoice(@PathVariable Long id) {
        invoiceService.voidInvoice(id);
        return Result.ok();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<PageResult<InvoiceVO>> page(@Valid InvoiceQuery query) {
        return Result.ok(invoiceService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<InvoiceVO> detail(@PathVariable Long id) {
        return Result.ok(invoiceService.detail(id));
    }
}
