package com.hospital.publichealth.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.publichealth.dto.CheckupPackageRequest;
import com.hospital.publichealth.dto.CheckupPackageVO;
import com.hospital.publichealth.dto.CheckupQuery;
import com.hospital.publichealth.dto.CheckupReportRequest;
import com.hospital.publichealth.dto.CheckupRequest;
import com.hospital.publichealth.dto.CheckupVO;
import com.hospital.publichealth.dto.HealthRecordQuery;
import com.hospital.publichealth.dto.HealthRecordRequest;
import com.hospital.publichealth.dto.HealthRecordVO;
import com.hospital.publichealth.dto.InfectiousQuery;
import com.hospital.publichealth.dto.InfectiousRequest;
import com.hospital.publichealth.dto.InfectiousVO;
import com.hospital.publichealth.dto.VaccinationQuery;
import com.hospital.publichealth.dto.VaccinationRequest;
import com.hospital.publichealth.dto.VaccinationVO;
import com.hospital.publichealth.service.CheckupPackageService;
import com.hospital.publichealth.service.CheckupService;
import com.hospital.publichealth.service.HealthRecordService;
import com.hospital.publichealth.service.InfectiousReportService;
import com.hospital.publichealth.service.VaccinationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * 公共卫生接口。
 *
 * <p>权限按动作拆：{@code health:query} 管所有列表与详情；
 * {@code health:record:edit} 管档案建档与更新；
 * {@code health:vaccine:add} 管接种登记；
 * {@code health:infectious:report} 管传染病填卡与上报；
 * {@code health:checkup:add} 管体检登记与标记已检；
 * {@code health:checkup:report} 管出报告；
 * {@code health:package:edit} 管体检套餐维护。</p>
 */
@Validated
@RestController
@RequestMapping("/health")
public class PublicHealthController {

    private final HealthRecordService recordService;
    private final VaccinationService vaccinationService;
    private final InfectiousReportService infectiousService;
    private final CheckupService checkupService;
    private final CheckupPackageService packageService;

    public PublicHealthController(HealthRecordService recordService,
                                  VaccinationService vaccinationService,
                                  InfectiousReportService infectiousService,
                                  CheckupService checkupService,
                                  CheckupPackageService packageService) {
        this.recordService = recordService;
        this.vaccinationService = vaccinationService;
        this.infectiousService = infectiousService;
        this.checkupService = checkupService;
        this.packageService = packageService;
    }

    /* ---------------- 健康档案 ---------------- */

    @PostMapping("/record")
    @PreAuthorize("hasAuthority('health:record:edit')")
    @AuditLog(module = "公共卫生", operation = "新建健康档案")
    public Result<String> createRecord(@Valid @RequestBody HealthRecordRequest request) {
        return Result.ok(String.valueOf(recordService.create(request)));
    }

    @PutMapping("/record/{id}")
    @PreAuthorize("hasAuthority('health:record:edit')")
    @AuditLog(module = "公共卫生", operation = "更新健康档案")
    public Result<Void> updateRecord(@PathVariable Long id, @Valid @RequestBody HealthRecordRequest request) {
        recordService.update(id, request);
        return Result.ok();
    }

    @GetMapping("/record/page")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<PageResult<HealthRecordVO>> recordPage(@Valid HealthRecordQuery query) {
        return Result.ok(recordService.page(query));
    }

    @GetMapping("/record/{id}")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<HealthRecordVO> recordDetail(@PathVariable Long id) {
        return Result.ok(recordService.detail(id));
    }

    /* ---------------- 疫苗接种 ---------------- */

    @PostMapping("/vaccination")
    @PreAuthorize("hasAuthority('health:vaccine:add')")
    @AuditLog(module = "公共卫生", operation = "登记疫苗接种")
    public Result<String> addVaccination(@Valid @RequestBody VaccinationRequest request) {
        return Result.ok(String.valueOf(vaccinationService.add(request)));
    }

    @GetMapping("/vaccination/page")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<PageResult<VaccinationVO>> vaccinationPage(@Valid VaccinationQuery query) {
        return Result.ok(vaccinationService.page(query));
    }

    /**
     * 接种提醒：未来 {@code days} 天内到期或已逾期的接种。
     *
     * <p>默认 30 天，上限 365 天（Service 内收敛），不传用默认值。</p>
     */
    @GetMapping("/vaccination/remind")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<List<VaccinationVO>> vaccinationRemind(
            @RequestParam(required = false) Integer days) {
        return Result.ok(vaccinationService.remind(days));
    }

    /* ---------------- 传染病上报 ---------------- */

    /** 填卡：先落一条待上报的卡片 */
    @PostMapping("/infectious")
    @PreAuthorize("hasAuthority('health:infectious:report')")
    @AuditLog(module = "公共卫生", operation = "登记传染病报告卡")
    public Result<String> createInfectious(@Valid @RequestBody InfectiousRequest request) {
        return Result.ok(String.valueOf(infectiousService.create(request)));
    }

    /** 上报疾控：0 待上报 → 1 已上报，上报后不可再改 */
    @PutMapping("/infectious/{id}/report")
    @PreAuthorize("hasAuthority('health:infectious:report')")
    @AuditLog(module = "公共卫生", operation = "传染病上报疾控")
    public Result<Void> reportInfectious(@PathVariable Long id) {
        infectiousService.report(id);
        return Result.ok();
    }

    /** 登记疾控反馈（只追加，不改病名与日期） */
    @PutMapping("/infectious/{id}/feedback")
    @PreAuthorize("hasAuthority('health:infectious:report')")
    @AuditLog(module = "公共卫生", operation = "登记疾控反馈")
    public Result<Void> feedbackInfectious(
            @PathVariable Long id,
            @RequestParam @NotBlank(message = "疾控反馈不能为空") String response) {
        infectiousService.feedback(id, response);
        return Result.ok();
    }

    @GetMapping("/infectious/page")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<PageResult<InfectiousVO>> infectiousPage(@Valid InfectiousQuery query) {
        return Result.ok(infectiousService.page(query));
    }

    @GetMapping("/infectious/{id}")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<InfectiousVO> infectiousDetail(@PathVariable Long id) {
        return Result.ok(infectiousService.detail(id));
    }

    /* ---------------- 体检管理 ---------------- */

    @PostMapping("/checkup")
    @PreAuthorize("hasAuthority('health:checkup:add')")
    @AuditLog(module = "公共卫生", operation = "体检登记")
    public Result<String> createCheckup(@Valid @RequestBody CheckupRequest request) {
        return Result.ok(String.valueOf(checkupService.create(request)));
    }

    /** 标记已检：0 待检 → 1 已检待出报告 */
    @PutMapping("/checkup/{id}/complete")
    @PreAuthorize("hasAuthority('health:checkup:add')")
    @AuditLog(module = "公共卫生", operation = "标记体检已检")
    public Result<Void> completeCheckup(@PathVariable Long id) {
        checkupService.complete(id);
        return Result.ok();
    }

    /** 出具体检报告：1 已检 → 2 已出报告 */
    @PutMapping("/checkup/{id}/report")
    @PreAuthorize("hasAuthority('health:checkup:report')")
    @AuditLog(module = "公共卫生", operation = "出具体检报告")
    public Result<Void> publishCheckupReport(@PathVariable Long id,
                                             @Valid @RequestBody CheckupReportRequest request) {
        checkupService.publishReport(id, request);
        return Result.ok();
    }

    @GetMapping("/checkup/page")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<PageResult<CheckupVO>> checkupPage(@Valid CheckupQuery query) {
        return Result.ok(checkupService.page(query));
    }

    @GetMapping("/checkup/{id}")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<CheckupVO> checkupDetail(@PathVariable Long id) {
        return Result.ok(checkupService.detail(id));
    }

    /* ---------------- 体检套餐 ---------------- */

    @PostMapping("/package")
    @PreAuthorize("hasAuthority('health:package:edit')")
    @AuditLog(module = "公共卫生", operation = "新建体检套餐")
    public Result<String> createPackage(@Valid @RequestBody CheckupPackageRequest request) {
        return Result.ok(String.valueOf(packageService.create(request)));
    }

    @PutMapping("/package/{id}")
    @PreAuthorize("hasAuthority('health:package:edit')")
    @AuditLog(module = "公共卫生", operation = "修改体检套餐")
    public Result<Void> updatePackage(@PathVariable Long id,
                                      @Valid @RequestBody CheckupPackageRequest request) {
        packageService.update(id, request);
        return Result.ok();
    }

    /** 启停套餐：历史体检单不受影响，它们存的是名称与价格快照 */
    @PutMapping("/package/{id}/status")
    @PreAuthorize("hasAuthority('health:package:edit')")
    @AuditLog(module = "公共卫生", operation = "启停体检套餐")
    public Result<Void> changePackageStatus(
            @PathVariable Long id,
            @RequestParam @NotNull(message = "状态不能为空") Integer status) {
        packageService.changeStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/package/list")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<List<CheckupPackageVO>> packageList(
            @RequestParam(required = false) Boolean onlyEnabled,
            @RequestParam(required = false) String keyword) {
        return Result.ok(packageService.list(onlyEnabled, keyword));
    }

    @GetMapping("/package/{id}")
    @PreAuthorize("hasAuthority('health:query')")
    public Result<CheckupPackageVO> packageDetail(@PathVariable Long id) {
        return Result.ok(packageService.detail(id));
    }
}
