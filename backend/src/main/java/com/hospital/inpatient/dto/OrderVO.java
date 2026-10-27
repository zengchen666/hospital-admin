package com.hospital.inpatient.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 医嘱视图对象。
 */
@Getter
@Builder
public class OrderVO {

    private final String id;

    private final String admissionId;

    /** 1 长期 / 2 临时 */
    private final Integer orderType;

    private final String content;

    private final Long drugId;

    private final String dosage;

    private final String frequency;

    private final LocalDateTime startTime;

    private final LocalDateTime stopTime;

    private final Long doctorId;

    private final String doctorName;

    /** 0 未执行 / 1 已执行 / 2 已停止 */
    private final Integer status;

    private final LocalDateTime createTime;
}
