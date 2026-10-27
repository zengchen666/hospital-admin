package com.hospital.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处方收费请求。
 *
 * <p>只传处方 ID、支付方式与优惠金额：应收总额由服务端按处方金额算出，
 * 前端传的「应收」一律忽略 —— 否则改个请求体就能少交钱。</p>
 */
@Getter
@Setter
public class ChargeRequest {

    @NotNull(message = "处方不能为空")
    private Long prescId;

    /** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式取值非法")
    @Max(value = 5, message = "支付方式取值非法")
    private Integer payType;

    /** 优惠金额，不能为负且不能超过应收 */
    @DecimalMin(value = "0.00", message = "优惠金额不能为负")
    private BigDecimal discountAmount;
}
