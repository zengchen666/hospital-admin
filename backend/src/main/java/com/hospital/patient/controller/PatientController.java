package com.hospital.patient.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.patient.dto.PatientPlainVO;
import com.hospital.patient.dto.PatientQuery;
import com.hospital.patient.dto.PatientSaveRequest;
import com.hospital.patient.dto.PatientVO;
import com.hospital.patient.service.PatientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

/**
 * 患者档案接口，对应文档 3.2。
 *
 * <p>查询类操作统一用 {@code patient:query}；解密查看单独用 {@code patient:decrypt}，
 * 并且<b>必须记审计</b> —— 谁能看患者身份证，一定要留痕。</p>
 */
@Validated
@RestController
@RequestMapping("/patient")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('patient:query')")
    public Result<PageResult<PatientVO>> page(@Valid PatientQuery query) {
        return Result.ok(patientService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:query')")
    public Result<PatientVO> detail(@PathVariable Long id) {
        return Result.ok(patientService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('patient:add')")
    @AuditLog(module = "患者管理", operation = "新增患者")
    public Result<String> create(@Valid @RequestBody PatientSaveRequest request) {
        return Result.ok(String.valueOf(patientService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:edit')")
    @AuditLog(module = "患者管理", operation = "修改患者")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PatientSaveRequest request) {
        patientService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:delete')")
    @AuditLog(module = "患者管理", operation = "删除患者")
    public Result<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return Result.ok();
    }

    /** 建档前的身份证查重，返回 true 表示已存在 */
    @GetMapping("/check/idcard")
    @PreAuthorize("hasAuthority('patient:add')")
    public Result<Boolean> checkIdCard(@RequestParam @NotBlank(message = "身份证号不能为空") String idCard) {
        return Result.ok(patientService.idCardExists(idCard));
    }

    /**
     * 解密查看身份证与手机号。
     *
     * <p>权限码与普通查询分开，且强制审计：这条记录回答了「谁在什么时候看了谁的身份证」。</p>
     */
    @GetMapping("/{id}/plain")
    @PreAuthorize("hasAuthority('patient:decrypt')")
    @AuditLog(module = "患者管理", operation = "解密查看患者敏感信息")
    public Result<PatientPlainVO> plain(@PathVariable Long id) {
        return Result.ok(patientService.plain(id));
    }
}
