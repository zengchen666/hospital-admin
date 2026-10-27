package com.hospital.finance.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 收费单查询条件。
 */
@Getter
@Setter
public class ChargeQuery extends PageQuery {

    private Long patientId;

    private Long visitId;

    private Long operatorId;

    private Integer chargeType;

    private Integer payStatus;

    private Integer payType;

    /** 1 处方 / 2 检查 / 3 挂号 / 4 住院 */
    private Integer sourceType;

    /** 收费单号（退费选单时按单号定位，前缀匹配） */
    private String chargeNo;
}
