package com.hospital.registration.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.registration.dto.RegistrationQuery;
import com.hospital.registration.dto.RegistrationRequest;
import com.hospital.registration.dto.RegistrationResult;
import com.hospital.registration.dto.RegistrationVO;
import com.hospital.registration.dto.ScheduleQuery;
import com.hospital.registration.dto.ScheduleSaveRequest;
import com.hospital.registration.dto.ScheduleVO;
import com.hospital.registration.service.RegistrationService;
import com.hospital.registration.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 挂号与预约接口，对应文档 4.2。
 *
 * <p>排班的增改走 {@code registration:schedule:edit}，挂号/退号/叫号各自独立权限码，
 * 便于按岗位授权（收费员能挂号，但改不了排班）。</p>
 */
@Validated
@RestController
@RequestMapping("/registration")
public class RegistrationController {

    private final ScheduleService scheduleService;
    private final RegistrationService registrationService;

    public RegistrationController(ScheduleService scheduleService,
                                  RegistrationService registrationService) {
        this.scheduleService = scheduleService;
        this.registrationService = registrationService;
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAuthority('registration:query')")
    public Result<PageResult<ScheduleVO>> schedulePage(@Valid ScheduleQuery query) {
        return Result.ok(scheduleService.page(query));
    }

    @GetMapping("/schedule/{id}")
    @PreAuthorize("hasAuthority('registration:query')")
    public Result<ScheduleVO> scheduleDetail(@PathVariable Long id) {
        return Result.ok(scheduleService.detail(id));
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('registration:schedule:edit')")
    @AuditLog(module = "挂号管理", operation = "新增排班")
    public Result<String> createSchedule(@Valid @RequestBody ScheduleSaveRequest request) {
        return Result.ok(String.valueOf(scheduleService.create(request)));
    }

    @PutMapping("/schedule/{id}/stop")
    @PreAuthorize("hasAuthority('registration:schedule:edit')")
    @AuditLog(module = "挂号管理", operation = "停诊")
    public Result<Void> stopSchedule(@PathVariable Long id) {
        scheduleService.stop(id);
        return Result.ok();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('registration:add')")
    @AuditLog(module = "挂号管理", operation = "挂号")
    public Result<RegistrationResult> register(@Valid @RequestBody RegistrationRequest request) {
        return Result.ok(registrationService.register(request));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('registration:cancel')")
    @AuditLog(module = "挂号管理", operation = "退号")
    public Result<Void> cancel(@PathVariable Long id) {
        registrationService.cancel(id);
        return Result.ok();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('registration:query')")
    public Result<PageResult<RegistrationVO>> page(@Valid RegistrationQuery query) {
        return Result.ok(registrationService.page(query));
    }

    /** 候诊队列：某科室当天待就诊 + 就诊中的挂号，按排队号升序 */
    @GetMapping("/queue/{deptId}")
    @PreAuthorize("hasAuthority('registration:call')")
    public Result<List<RegistrationVO>> queue(
            @PathVariable Long deptId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(registrationService.queue(deptId, date));
    }

    @PostMapping("/call/{id}")
    @PreAuthorize("hasAuthority('registration:call')")
    @AuditLog(module = "挂号管理", operation = "叫号")
    public Result<Void> call(@PathVariable Long id) {
        registrationService.call(id);
        return Result.ok();
    }
}
