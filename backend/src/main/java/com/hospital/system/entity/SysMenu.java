package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单表 sys_menu，驱动前端动态路由。
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;

    private String menuName;

    /** 1 目录 / 2 菜单 / 3 按钮 */
    private Integer menuType;

    private String path;

    private String component;

    private String perms;

    private String icon;

    private Integer sort;

    /** 0 隐藏 / 1 显示 */
    private Integer visible;

    /** 0 停用 / 1 正常 */
    private Integer status;

    private String remark;
}
