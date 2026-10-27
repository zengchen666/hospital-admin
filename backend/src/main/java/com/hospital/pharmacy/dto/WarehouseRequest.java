package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 库房新增。
 */
@Getter
@Setter
public class WarehouseRequest {

    @NotBlank(message = "库房名称不能为空")
    @Size(max = 50, message = "库房名称长度不能超过 50")
    private String warehouseName;

    @NotBlank(message = "库房编码不能为空")
    @Size(max = 30, message = "库房编码长度不能超过 30")
    private String warehouseCode;

    /** 1 药库 / 2 药房 / 3 科室备药柜 */
    @Min(value = 1, message = "库房类型不能为空")
    private Integer warehouseType;

    private Long deptId;

    private Long managerId;
}
