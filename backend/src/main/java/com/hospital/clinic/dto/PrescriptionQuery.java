package com.hospital.clinic.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 处方查询条件。
 */
@Getter
@Setter
public class PrescriptionQuery extends PageQuery {

    private Long visitId;

    private Long patientId;

    private Long doctorId;

    /** 0 待审核 / 1 已审核 / 2 已收费 / 3 已发药 / 4 已退 */
    private Integer status;

    /** 1 西药 / 2 中药 / 3 中成药 */
    private Integer prescType;
}
