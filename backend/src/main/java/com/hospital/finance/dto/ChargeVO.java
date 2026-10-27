package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收费单视图对象（含明细）。
 */
@Getter
@Builder
public class ChargeVO {

    private final String id;

    private final String chargeNo;

    private final Long patientId;

    private final String patientName;

    private final Long visitId;

    private final Integer sourceType;

    private final String sourceId;

    /** 1 挂号 / 2 门诊 / 3 住院 */
    private final Integer chargeType;

    private final BigDecimal totalAmount;

    private final BigDecimal discountAmount;

    private final BigDecimal actualAmount;

    private final Integer payType;

    /** 0 待支付 / 1 已支付 / 2 已退费 / 3 部分退费 */
    private final Integer payStatus;

    private final Long operatorId;

    private final String operatorName;

    private final LocalDateTime chargeTime;

    private final List<ChargeItemVO> items;
}
