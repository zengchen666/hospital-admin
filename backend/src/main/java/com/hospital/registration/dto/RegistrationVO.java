package com.hospital.registration.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 挂号记录视图对象。
 */
@Getter
@Builder
public class RegistrationVO {

    private final String id;

    private final String regNo;

    private final Long patientId;

    private final String patientName;

    private final String patientNo;

    private final Long doctorId;

    private final String doctorName;

    private final Long deptId;

    private final Long scheduleId;

    /** 1 普通 / 2 专家 / 3 急诊 */
    private final Integer regType;

    private final BigDecimal regFee;

    private final Integer queueNo;

    /** 0 待就诊 / 1 就诊中 / 2 已完成 / 3 已退号 */
    private final Integer status;

    private final LocalDateTime regTime;
}
