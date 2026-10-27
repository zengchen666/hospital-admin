package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 权限点视图。
 *
 * <p>{@code module} 由权限码前缀推导（{@code patient:query → patient}），
 * 前端按它分组展示。权限码本身「模块:资源:动作」三段式已经是约定，
 * 前缀即模块，不需要再单独维护一张模块表。</p>
 */
@Getter
@Setter
public class PermissionVO {

    private String id;

    private String permCode;

    private String permName;

    /** 1 菜单 / 2 按钮 / 3 接口 */
    private Integer permType;

    private Integer dataScope;

    private Integer status;

    /** 权限码前缀，即模块名 */
    private String module;

    public static PermissionVO of(com.hospital.system.entity.SysPermission entity) {
        PermissionVO vo = new PermissionVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setPermCode(entity.getPermCode());
        vo.setPermName(entity.getPermName());
        vo.setPermType(entity.getPermType());
        vo.setDataScope(entity.getDataScope());
        vo.setStatus(entity.getStatus());
        String code = entity.getPermCode();
        vo.setModule(code == null || !code.contains(":") ? code : code.substring(0, code.indexOf(':')));
        return vo;
    }
}
