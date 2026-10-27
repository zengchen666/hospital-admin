package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 库房视图对象。
 */
@Getter
@Builder
public class WarehouseVO {

    private final String id;

    private final String warehouseName;

    private final String warehouseCode;

    /** 1 药库 / 2 药房 / 3 科室备药柜 */
    private final Integer warehouseType;

    private final String deptId;

    private final String managerId;

    private final String managerName;

    /** 0 停用 / 1 启用 */
    private final Integer status;
}
