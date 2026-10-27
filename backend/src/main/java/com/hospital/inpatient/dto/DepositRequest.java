package com.hospital.inpatient.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 预交金充值请求。
 *
 * <p>金额必须为正：退款走退费流程，不能靠「充负数」把钱退出去。</p>
 */
@Getter
@Setter
public class DepositRequest {

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    private BigDecimal amount;
}
