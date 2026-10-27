package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退费单视图对象。
 */
@Getter
@Builder
public class RefundVO {

    private final String id;

    private final String refundNo;

    private final String chargeId;

    private final String chargeNo;

    private final Long patientId;

    private final String patientName;

    private final BigDecimal refundAmount;

    /** 1 全额 / 2 部分 */
    private final Integer refundType;

    private final String reason;

    /** 0 待审核 / 1 已审核 / 2 已退款 / 3 已驳回 */
    private final Integer status;

    private final Long applicantId;

    private final String applicantName;

    private final Long auditorId;

    private final String auditorName;

    private final LocalDateTime auditTime;

    private final LocalDateTime refundTime;

    private final LocalDateTime createTime;

    private final List<RefundItemVO> items;
}
