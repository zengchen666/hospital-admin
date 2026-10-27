package com.hospital.system.controller;

import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.system.dto.ConfigQuery;
import com.hospital.system.dto.ConfigRequest;
import com.hospital.system.dto.ConfigVO;
import com.hospital.system.service.SysConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统参数接口（模块一「系统参数」）。
 *
 * <p>权限与字典同构：读 {@code system:config:query}，写 {@code system:config:edit}。
 * 参数改动会立刻影响全站行为（比如把预约提前天数从 7 改成 1），
 * 因此每一次写都进审计，出问题时能查到是谁在什么时候改的。</p>
 */
@Validated
@RestController
@RequestMapping("/system/config")
public class SysConfigController {

    private final SysConfigService configService;

    public SysConfigController(SysConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:config:query')")
    public Result<List<ConfigVO>> list(ConfigQuery query) {
        return Result.ok(configService.list(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:query')")
    public Result<ConfigVO> detail(@PathVariable Long id) {
        return Result.ok(configService.detail(id));
    }

    /** 按键取值，未配置返回 null */
    @GetMapping("/value/{configKey}")
    @PreAuthorize("hasAuthority('system:config:query')")
    public Result<String> value(@PathVariable String configKey) {
        return Result.ok(configService.value(configKey));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:config:edit')")
    @AuditLog(module = "系统管理", operation = "新建系统参数")
    public Result<Long> create(@Valid @RequestBody ConfigRequest request) {
        return Result.ok(configService.create(request));
    }

    /** 修改参数值（不接收 configKey，见 Service 注释） */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:edit')")
    @AuditLog(module = "系统管理", operation = "修改系统参数")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ConfigRequest request) {
        configService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:edit')")
    @AuditLog(module = "系统管理", operation = "删除系统参数")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.ok();
    }
}
