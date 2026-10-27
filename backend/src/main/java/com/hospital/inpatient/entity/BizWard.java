package com.hospital.inpatient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 病区 biz_ward。
 */
@Getter
@Setter
@TableName("biz_ward")
public class BizWard extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String wardName;

    private String wardCode;

    private Long deptId;

    private String floor;

    /** 床位数，由床位表维护 */
    private Integer bedCount;

    private Long nurseHead;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
