package com.hospital.system.controller;

import com.hospital.common.api.Result;
import com.hospital.system.dto.MenuVO;
import com.hospital.system.service.SysMenuService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单接口，对应文档 2.2 的 {@code GET /api/system/menu/tree}。
 */
@RestController
@RequestMapping("/system/menu")
public class SysMenuController {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    /** 当前用户可见的动态路由树 */
    @GetMapping("/tree")
    public Result<List<MenuVO>> tree() {
        return Result.ok(menuService.currentUserMenuTree());
    }

    /**
     * 全量菜单树（菜单管理页用，含停用与按钮类型）。
     *
     * <p>单独授权 {@code system:menu:query}：能看菜单结构不等于能改菜单结构。
     * 改菜单会立刻影响所有人的侧边栏，误操作的波及面是全院。</p>
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('system:menu:query')")
    public Result<List<MenuVO>> all() {
        return Result.ok(menuService.allMenuTree());
    }
}
