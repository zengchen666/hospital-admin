package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色 sys_role。
 *
 * <p>一期角色是<b>预置</b>的（seed 里 7 个），不提供增删改 ——
 * 角色一旦能被随意创建，权限矩阵就成了摆设。这里只做读，给「新增用户选角色」用。</p>
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String roleName;

    private String roleCode;

    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
