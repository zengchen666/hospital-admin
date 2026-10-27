package com.hospital.clinic.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方视图对象（含明细）。
 */
@Getter
@Builder
public class PrescriptionVO {

    private final String id;

    private final String prescNo;

    private final Long patientId;

    private final String patientName;

    private final Long doctorId;

    private final String doctorName;

    private final String visitId;

    /** 1 西药 / 2 中药 / 3 中成药 */
    private final Integer prescType;

    private final String diagnosis;

    private final BigDecimal totalAmount;

    /** 0 待审核 / 1 已审核 / 2 已收费 / 3 已发药 / 4 已退 */
    private final Integer status;

    private final LocalDateTime createTime;

    private final List<PrescriptionItemVO> items;
}
