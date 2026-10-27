package com.hospital.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 检查检验收费请求。
 *
 * <p>与处方收费一样：<b>应收金额由服务端按价目表算出</b>，前端只能传支付方式
 * 和优惠金额。让前端传「应收多少」等于把定价权交给了浏览器。</p>
 */
@Getter
@Setter
public class ExamChargeRequest {

    @NotNull(message = "检查单不能为空")
    private Long applyId;

    /** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式取值非法")
    @Max(value = 5, message = "支付方式取值非法")
    private Integer payType;

    @DecimalMin(value = "0.00", message = "优惠金额不能为负")
    private BigDecimal discountAmount;
}
