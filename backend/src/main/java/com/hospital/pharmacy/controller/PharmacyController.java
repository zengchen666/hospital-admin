package com.hospital.pharmacy.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.pharmacy.dto.DispenseQuery;
import com.hospital.pharmacy.dto.DispenseRequest;
import com.hospital.pharmacy.dto.DispenseResult;
import com.hospital.pharmacy.dto.DispenseVO;
import com.hospital.pharmacy.dto.PendingPrescVO;
import com.hospital.pharmacy.entity.BizWarehouse;
import com.hospital.pharmacy.service.DispenseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 药房接口：门诊发药与发药单查询。
 *
 * <p>发药是「账实一致」的最后一环：扣库存、留流水、推进处方状态三件事
 * 必须在同一事务里完成，见 {@link DispenseService}。</p>
 */
@Validated
@RestController
@RequestMapping("/pharmacy")
public class PharmacyController {

    private final DispenseService dispenseService;

    public PharmacyController(DispenseService dispenseService) {
        this.dispenseService = dispenseService;
    }

    /** 门诊发药 */
    @PostMapping("/dispense")
    @PreAuthorize("hasAuthority('pharmacy:dispense')")
    @AuditLog(module = "药房管理", operation = "门诊发药")
    public Result<DispenseResult> dispense(@Valid @RequestBody DispenseRequest request) {
        return Result.ok(dispenseService.dispense(request));
    }

    @GetMapping("/dispense/page")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<PageResult<DispenseVO>> page(@Valid DispenseQuery query) {
        return Result.ok(dispenseService.page(query));
    }

    @GetMapping("/dispense/{id}")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<DispenseVO> detail(@PathVariable Long id) {
        return Result.ok(dispenseService.detail(id));
    }

    /** 发药库房下拉（只返回启用的药房） */
    @GetMapping("/warehouse/options")
    @PreAuthorize("hasAuthority('pharmacy:dispense')")
    public Result<List<BizWarehouse>> warehouseOptions() {
        return Result.ok(dispenseService.warehouseOptions());
    }

    /**
     * 待发药处方列表（已收费未发药）。
     *
     * <p>这是药房窗口的工作队列：只列<b>已收费</b>的处方。
     * 未收费的处方发出去，钱没收、药先走了，后面补收会变成一笔说不清的账。</p>
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('pharmacy:dispense')")
    public Result<PageResult<PendingPrescVO>> pending(
            @RequestParam(required = false) String prescNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(dispenseService.pending(prescNo, patientName, pageNum, pageSize));
    }
}
