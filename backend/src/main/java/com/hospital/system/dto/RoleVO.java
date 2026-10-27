package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图（含已授权权限）。
 *
 * <p>角色管理页要能回答一个问题：<b>「这个角色到底能干什么」</b>。
 * 只列角色名等于什么都没说 —— 真正决定权限的是它挂了哪些权限码。
 * 因此这里把权限码一起带出去，按模块前缀分好组。</p>
 */
@Getter
@Setter
public class RoleVO {

    private String id;

    private String roleName;

    private String roleCode;

    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status;

    /** 已启用的权限码 */
    private List<String> permCodes;

    /** 已启用权限数量 */
    private Integer permCount;

    /** 该角色下的用户数 */
    private Integer userCount;

    private LocalDateTime createTime;
}
