package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 库房 biz_warehouse。
 */
@Getter
@Setter
@TableName("biz_warehouse")
public class BizWarehouse extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String warehouseName;

    private String warehouseCode;

    /** 1 药库 / 2 药房 / 3 科室备药柜 */
    private Integer warehouseType;

    private Long deptId;

    private Long managerId;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
