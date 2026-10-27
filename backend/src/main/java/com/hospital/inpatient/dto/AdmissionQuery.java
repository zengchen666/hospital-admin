package com.hospital.inpatient.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 住院查询条件。
 */
@Getter
@Setter
public class AdmissionQuery extends PageQuery {

    private Long patientId;

    private Long deptId;

    private Long doctorId;

    private Long wardId;

    /** 0 在院 / 1 出院 / 2 转科 */
    private Integer status;
}
