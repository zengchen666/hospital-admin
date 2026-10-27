package com.hospital.publichealth.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 体检记录分页查询。 */
@Getter
@Setter
public class CheckupQuery extends PageQuery {

    private Long patientId;

    private Long packageId;

    /** 0 待检 / 1 已检待出报告 / 2 已出报告 */
    private Integer status;
}
