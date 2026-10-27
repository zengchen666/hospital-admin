package com.hospital.clinic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 处方明细入参。价格与名称不在这里传。
 */
@Getter
@Setter
public class PrescriptionItemRequest {

    @NotNull(message = "药品不能为空")
    private Long drugId;

    @Size(max = 100, message = "用法长度不能超过 100")
    private String usage;

    @Size(max = 50, message = "用量长度不能超过 50")
    private String dosage;

    @Size(max = 20, message = "频次长度不能超过 20")
    private String frequency;

    @Min(value = 1, message = "用药天数至少为 1")
    @Max(value = 365, message = "用药天数不能超过 365")
    private Integer days;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 9999, message = "单条明细数量不能超过 9999")
    private Integer quantity;
}
