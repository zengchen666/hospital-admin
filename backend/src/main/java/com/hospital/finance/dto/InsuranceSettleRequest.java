package com.hospital.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 医保结算请求。
 *
 * <p>金额一律由服务端按收费单与报销比例算出：
 * 报销多少是医保目录说了算的，不是窗口能手工填的。</p>
 */
@Getter
@Setter
public class InsuranceSettleRequest {

    @NotNull(message = "收费单不能为空")
    private Long chargeId;

    /** 职工 / 居民 / 新农合 / 商保 */
    @NotBlank(message = "医保类型不能为空")
    @Size(max = 30, message = "医保类型长度不能超过 30")
    private String insuranceType;
}
