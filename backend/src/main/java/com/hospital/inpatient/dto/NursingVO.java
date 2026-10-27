package com.hospital.inpatient.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 护理记录视图对象。
 *
 * <p>前端体温图需要 admissionId + patientName，所以 VO 里带上，
 * 免得前端为了画一张图再查一次住院接口。</p>
 */
@Getter
@Builder
public class NursingVO {

    private final String id;

    private final String admissionId;

    private final String patientName;

    private final String bedNo;

    /** 1 护理记录 / 2 体温单 / 3 生命体征 */
    private final Integer recordType;

    private final LocalDateTime recordTime;

    private final BigDecimal temperature;

    private final Integer pulse;

    private final Integer breath;

    private final String bloodPressure;

    private final Integer spo2;

    private final Integer intake;

    private final Integer output;

    private final String content;

    private final Long nurseId;

    private final String nurseName;
}
