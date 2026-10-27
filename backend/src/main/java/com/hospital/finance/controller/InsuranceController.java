package com.hospital.finance.controller;

import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.finance.dto.InsuranceSettleRequest;
import com.hospital.finance.dto.InsuranceSettleVO;
import com.hospital.finance.service.InsuranceService;
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
 * 医保结算（一期 Mock）。
 *
 * <p>接口形状按真医保接口留：上送 { 收费单、参保类型、总额 }，
 * 返回 { 统筹支付、个人自付 }。将来换真实现只替换 Service，
 * 控制器与表结构都不用动。</p>
 */
@Validated
@RestController
@RequestMapping("/finance/insurance")
public class InsuranceController {

    private final InsuranceService insuranceService;

    public InsuranceController(InsuranceService insuranceService) {
        this.insuranceService = insuranceService;
    }

    @PostMapping("/settle")
    @PreAuthorize("hasAuthority('finance:charge:add')")
    @AuditLog(module = "收费管理", operation = "医保结算")
    public Result<String> settle(@Valid @RequestBody InsuranceSettleRequest request) {
        return Result.ok(String.valueOf(insuranceService.settle(request)));
    }

    @GetMapping("/settle/{chargeId}")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<InsuranceSettleVO> detail(@PathVariable Long chargeId) {
        return Result.ok(insuranceService.detailByCharge(chargeId));
    }
}
