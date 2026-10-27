package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端动态路由节点。
 */
@Getter
@Setter
public class MenuVO {

    private String id;

    private String parentId;

    private String menuName;

    /** 1 目录 / 2 菜单 / 3 按钮 */
    private Integer menuType;

    private String path;

    private String component;

    private String perms;

    private String icon;

    private Integer sort;

    private List<MenuVO> children = new ArrayList<>();
}
