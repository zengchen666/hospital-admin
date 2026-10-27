package com.hospital.publichealth.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 接种记录分页查询。 */
@Getter
@Setter
public class VaccinationQuery extends PageQuery {

    private Long patientId;

    private String vaccineName;

    private String batchNo;
}
