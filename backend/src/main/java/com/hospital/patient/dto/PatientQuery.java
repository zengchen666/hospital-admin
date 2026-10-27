package com.hospital.patient.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者分页查询条件。
 */
@Getter
@Setter
public class PatientQuery extends PageQuery {

    /** 0 女 / 1 男 / 2 未知 */
    private Integer gender;

    /** A/B/AB/O/未知 */
    private String bloodType;

    /** 按科室筛选（该科室有挂号记录的患者） */
    private Long deptId;
}
