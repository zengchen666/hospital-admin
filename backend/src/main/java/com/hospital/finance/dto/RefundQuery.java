package com.hospital.finance.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 退费查询条件。
 */
@Getter
@Setter
public class RefundQuery extends PageQuery {

    private Long chargeId;

    private Long patientId;

    /** 0 待审核 / 1 已审核 / 2 已退款 / 3 已驳回 */
    private Integer status;
}
