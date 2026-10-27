package com.hospital.finance.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 发票查询条件。
 */
@Getter
@Setter
public class InvoiceQuery extends PageQuery {

    private String invoiceNo;

    private String chargeNo;

    private Long patientId;

    /** 1 电子 / 2 纸质 */
    private Integer invoiceType;

    /** 0 正常 / 1 已作废 */
    private Integer status;
}
