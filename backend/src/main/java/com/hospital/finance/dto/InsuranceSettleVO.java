package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保结算视图对象。
 */
@Getter
@Builder
public class InsuranceSettleVO {

    private final String id;

    private final String settleNo;

    private final String chargeId;

    private final String chargeNo;

    private final Long patientId;

    private final String patientName;

    private final String insuranceType;

    private final BigDecimal totalAmount;

    private final BigDecimal insuranceAmount;

    private final BigDecimal selfAmount;

    /** 0 待结算 / 1 已结算 / 2 结算失败 */
    private final Integer settleStatus;

    private final String failReason;

    private final LocalDateTime settleTime;

    private final LocalDateTime createTime;
}
