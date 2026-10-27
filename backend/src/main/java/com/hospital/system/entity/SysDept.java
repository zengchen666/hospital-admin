package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室 sys_dept。
 *
 * <p>只做<b>只读</b>：科室是组织架构，由 DBA 通过 {@code sql/} 补丁脚本维护，
 * 与角色同理 —— 谁能随手建科室，权限矩阵与统计口径就都没意义了。</p>
 */
@Getter
@Setter
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;

    private String deptName;

    private String deptCode;

    /** 1 门诊 / 2 住院 / 3 医技 */
    private Integer deptType;

    private Integer sort;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
