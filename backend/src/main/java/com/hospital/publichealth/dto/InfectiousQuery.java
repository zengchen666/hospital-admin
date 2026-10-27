package com.hospital.publichealth.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 传染病报告卡分页查询。 */
@Getter
@Setter
public class InfectiousQuery extends PageQuery {

    private Long patientId;

    private String diseaseName;

    /** 0 待上报 / 1 已上报 */
    private Integer reportStatus;
}
