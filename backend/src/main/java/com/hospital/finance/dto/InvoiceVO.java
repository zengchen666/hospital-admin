package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票视图对象。
 */
@Getter
@Builder
public class InvoiceVO {

    private final String id;

    private final String invoiceNo;

    private final String chargeId;

    private final String chargeNo;

    private final Long patientId;

    private final String patientName;

    /** 1 电子 / 2 纸质 */
    private final Integer invoiceType;

    private final BigDecimal amount;

    /** 0 正常 / 1 已作废 */
    private final Integer status;

    private final LocalDateTime printTime;

    private final String operatorName;

    private final LocalDateTime createTime;
}
