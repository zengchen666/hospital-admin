package com.hospital.system.controller;

import com.hospital.common.api.Result;
import com.hospital.system.dto.PermissionVO;
import com.hospital.system.dto.RoleVO;
import com.hospital.system.entity.SysRole;
import com.hospital.system.mapper.SysRoleMapper;
import com.hospital.system.service.SysRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色只读接口。
 *
 * <p>只提供查询：一期角色是预置的，不给增删改 ——
 * 角色如果能被随意创建，需求文档里那张角色 × 模块权限矩阵就成了摆设。
 * 角色授权由 DBA 通过 {@code sql/} 下的补丁脚本维护，脚本即变更记录。</p>
 */
@RestController
@RequestMapping("/system/role")
public class SysRoleController {

    private final SysRoleMapper roleMapper;
    private final SysRoleService roleService;

    public SysRoleController(SysRoleMapper roleMapper, SysRoleService roleService) {
        this.roleMapper = roleMapper;
        this.roleService = roleService;
    }

    /** 角色下拉选项（只返回启用的） */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getId)));
    }

    /** 角色列表（含停用），带权限数与用户数 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<RoleVO>> roles() {
        return Result.ok(roleService.list());
    }

    /** 角色详情，含已授权的权限码 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.detail(id));
    }

    /** 角色已持有的权限 */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<PermissionVO>> permissions(@PathVariable Long id) {
        return Result.ok(roleService.permissions(id));
    }

    /**
     * 权限总表 + 该角色是否已持有。
     *
     * <p>只读：界面可以据此展示「这个角色能做什么」，但不能勾选保存。
     * 授权变更必须落在补丁脚本里，这样才有 review 记录和回滚路径。</p>
     */
    @GetMapping("/{id}/permission-matrix")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<PermissionVO>> permissionMatrix(@PathVariable Long id) {
        return Result.ok(roleService.permissionMatrix(id));
    }

    /** 全量权限点字典，供前端按模块分组展示 */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<PermissionVO>> allPermissions() {
        return Result.ok(roleService.allPermissions());
    }
}
