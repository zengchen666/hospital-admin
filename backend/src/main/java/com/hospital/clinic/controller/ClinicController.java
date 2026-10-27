package com.hospital.clinic.controller;

import com.hospital.clinic.dto.MedicalRecordRequest;
import com.hospital.clinic.dto.MedicalRecordVO;
import com.hospital.clinic.dto.PrescriptionQuery;
import com.hospital.clinic.dto.PrescriptionRequest;
import com.hospital.clinic.dto.PrescriptionVO;
import com.hospital.clinic.dto.ReceiveResult;
import com.hospital.clinic.service.ClinicVisitService;
import com.hospital.clinic.service.MedicalRecordService;
import com.hospital.clinic.service.PrescriptionService;
import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.pharmacy.dto.DrugOptionVO;
import com.hospital.pharmacy.service.DrugQueryService;
import com.hospital.registration.dto.RegistrationQuery;
import com.hospital.registration.dto.RegistrationVO;
import com.hospital.registration.service.RegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
 * 门诊工作台接口，对应文档 5.2。
 *
 * <p>权限划分：</p>
 * <ul>
 *   <li>{@code clinic:query} —— 就诊列表、病历、处方查询</li>
 *   <li>{@code clinic:receive} —— 接诊、写病历</li>
 *   <li>{@code clinic:prescribe} —— 开方、作废</li>
 *   <li>{@code clinic:presc:audit} —— 处方审核（应与开方分属不同岗位）</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/clinic")
public class ClinicController {

    private final ClinicVisitService visitService;
    private final MedicalRecordService recordService;
    private final PrescriptionService prescriptionService;
    private final RegistrationService registrationService;
    private final DrugQueryService drugQueryService;

    public ClinicController(ClinicVisitService visitService,
                            MedicalRecordService recordService,
                            PrescriptionService prescriptionService,
                            RegistrationService registrationService,
                            DrugQueryService drugQueryService) {
        this.visitService = visitService;
        this.recordService = recordService;
        this.prescriptionService = prescriptionService;
        this.registrationService = registrationService;
        this.drugQueryService = drugQueryService;
    }

    /**
     * 就诊列表。
     *
     * <p>直接复用挂号分页：一期「一次挂号 = 一次就诊」，再单独造一张就诊表纯属冗余。
     * 前端工作台一般传 {@code status=0}（待就诊）或 {@code status=1}（就诊中）。</p>
     */
    @GetMapping("/visit/page")
    @PreAuthorize("hasAuthority('clinic:query')")
    public Result<PageResult<RegistrationVO>> visitPage(@Valid RegistrationQuery query) {
        return Result.ok(registrationService.page(query));
    }

    @PostMapping("/visit/{id}/receive")
    @PreAuthorize("hasAuthority('clinic:receive')")
    @AuditLog(module = "门诊管理", operation = "接诊")
    public Result<ReceiveResult> receive(@PathVariable Long id) {
        return Result.ok(visitService.receive(id));
    }

    @PostMapping("/record")
    @PreAuthorize("hasAuthority('clinic:receive')")
    @AuditLog(module = "门诊管理", operation = "保存病历")
    public Result<String> saveRecord(@Valid @RequestBody MedicalRecordRequest request) {
        return Result.ok(String.valueOf(recordService.save(request)));
    }

    /** 按就诊取病历；尚未书写时返回 {@code data=null} 而不是报错 */
    @GetMapping("/record")
    @PreAuthorize("hasAuthority('clinic:query')")
    public Result<MedicalRecordVO> getRecord(@RequestParam @NotNull(message = "就诊 ID 不能为空") Long visitId) {
        return Result.ok(recordService.getByVisit(visitId));
    }

    @GetMapping("/prescription/page")
    @PreAuthorize("hasAuthority('clinic:query')")
    public Result<PageResult<PrescriptionVO>> prescriptionPage(@Valid PrescriptionQuery query) {
        return Result.ok(prescriptionService.page(query));
    }

    @GetMapping("/prescription/{id}")
    @PreAuthorize("hasAuthority('clinic:query')")
    public Result<PrescriptionVO> prescriptionDetail(@PathVariable Long id) {
        return Result.ok(prescriptionService.detail(id));
    }

    @PostMapping("/prescription")
    @PreAuthorize("hasAuthority('clinic:prescribe')")
    @AuditLog(module = "门诊管理", operation = "开处方")
    public Result<String> createPrescription(@Valid @RequestBody PrescriptionRequest request) {
        return Result.ok(String.valueOf(prescriptionService.create(request)));
    }

    @PutMapping("/prescription/{id}/audit")
    @PreAuthorize("hasAuthority('clinic:presc:audit')")
    @AuditLog(module = "门诊管理", operation = "处方审核")
    public Result<Void> auditPrescription(@PathVariable Long id) {
        prescriptionService.audit(id);
        return Result.ok();
    }

    @DeleteMapping("/prescription/{id}")
    @PreAuthorize("hasAuthority('clinic:prescribe')")
    @AuditLog(module = "门诊管理", operation = "作废处方")
    public Result<Void> cancelPrescription(@PathVariable Long id) {
        prescriptionService.cancel(id);
        return Result.ok();
    }

    /**
     * 开方选药用的药品选项。
     *
     * <p>放在门诊路由下是因为它服务于开方场景；药品维护本身属于药房模块。</p>
     */
    @GetMapping("/drug/options")
    @PreAuthorize("hasAuthority('clinic:prescribe')")
    public Result<List<DrugOptionVO>> drugOptions(@RequestParam(required = false) String keyword) {
        return Result.ok(drugQueryService.options(keyword));
    }
}
