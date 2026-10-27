package com.hospital.finance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 开票请求。
 *
 * <p><b>金额不由前端传</b>：发票金额必须等于收费单实收金额，
 * 让前端传一个数字，等于允许「收 100 开 50 的票」。</p>
 */
@Getter
@Setter
public class InvoicePrintRequest {

    @NotNull(message = "收费单不能为空")
    private Long chargeId;

    /** 1 电子 / 2 纸质 */
    @NotNull(message = "发票类型不能为空")
    @Min(value = 1, message = "发票类型取值非法")
    @Max(value = 2, message = "发票类型取值非法")
    private Integer invoiceType;
}
