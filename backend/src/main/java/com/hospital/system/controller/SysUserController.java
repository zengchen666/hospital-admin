package com.hospital.system.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.system.dto.UserQuery;
import com.hospital.system.dto.UserSaveRequest;
import com.hospital.system.dto.UserOptionVO;
import com.hospital.system.dto.UserVO;
import com.hospital.system.service.SysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * 用户管理接口，对应文档 2.2。
 *
 * <p>权限码与菜单 perms 一致：查询 system:user:query，增删改分别是 add/edit/delete，
 * 重置密码 system:user:reset。</p>
 */
@Validated
@RestController
@RequestMapping("/system/user")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<PageResult<UserVO>> page(@Valid UserQuery query) {
        return Result.ok(userService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    /**
     * 用户下拉选项（排班选医生、医嘱选执行人）。
     *
     * <p>判权用 {@code isAuthenticated()}：下拉里只有姓名和科室，不含敏感信息，
     * 而挂号、住院、排班多个模块都要用 —— 挂到 {@code system:user:query} 上
     * 会让护士排不了班。</p>
     */
    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public Result<List<UserOptionVO>> options(@RequestParam(required = false) String roleCode) {
        return Result.ok(userService.options(roleCode));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @AuditLog(module = "用户管理", operation = "新增用户")
    public Result<String> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(String.valueOf(userService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(module = "用户管理", operation = "修改用户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @AuditLog(module = "用户管理", operation = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(module = "用户管理", operation = "启用/禁用用户")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('system:user:reset')")
    @AuditLog(module = "用户管理", operation = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestParam @NotBlank(message = "新密码不能为空")
                                      @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间") String newPassword) {
        userService.resetPassword(id, newPassword);
        return Result.ok();
    }
}
