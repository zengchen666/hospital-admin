package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 采购明细请求。
 *
 * <p>{@code amount} 由服务端按 quantity × price 计算，不接受前端传值 ——
 * 单据金额让人传，等于把篡改成本降到改一个 JSON 字段。</p>
 */
@Getter
@Setter
public class PurchaseItemRequest {

    @NotNull(message = "药品不能为空")
    private Long drugId;

    @NotNull(message = "采购数量不能为空")
    @Min(value = 1, message = "采购数量必须大于 0")
    private Integer quantity;

    @NotNull(message = "采购单价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "采购单价必须大于 0")
    private BigDecimal price;
}
