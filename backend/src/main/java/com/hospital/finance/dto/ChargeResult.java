package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 收费回执。
 */
@Getter
@Builder
public class ChargeResult {

    private final String id;

    private final String chargeNo;

    private final BigDecimal totalAmount;

    private final BigDecimal discountAmount;

    private final BigDecimal actualAmount;

    /** 1 已支付 */
    private final Integer payStatus;
}
