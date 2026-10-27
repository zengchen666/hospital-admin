package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限点 sys_permission。
 *
 * <p>权限码（{@code perm_code}）是本系统的<b>事实主键</b>：
 * Spring Security 的 {@code hasAuthority} 按它判权，SQL 补丁按它授权，
 * 前端 {@code v-perm} 也按它比对。{@code id} 只是外键载体 ——
 * 这也是为什么新增权限时必须先查 {@code perm_code} 是否已存在，
 * 而不能只看 id 空位（id 撞车会静默改写别人的权限名）。</p>
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 权限码，如 patient:query */
    private String permCode;

    private String permName;

    /** 1 菜单 / 2 按钮 / 3 接口 */
    private Integer permType;

    private Long menuId;

    /** 数据范围：1 全部 / 2 本部门 / 3 本人 */
    private Integer dataScope;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
