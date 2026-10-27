package com.hospital.inpatient.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院登记视图对象。
 */
@Getter
@Builder
public class AdmissionVO {

    private final String id;

    private final String admissionNo;

    private final Long patientId;

    private final String patientName;

    private final String patientNo;

    private final Long deptId;

    private final Long bedId;

    private final String bedNo;

    private final Long wardId;

    private final String wardName;

    private final Long doctorId;

    private final String doctorName;

    private final LocalDateTime admissionTime;

    private final LocalDateTime dischargeTime;

    private final String diagnosis;

    private final BigDecimal deposit;

    /** 0 在院 / 1 出院 / 2 转科 */
    private final Integer status;

    /** 已住天数（在院时按当前时间算，出院时按出院时间算），null 表示未入院 */
    private final Long stayDays;
}
