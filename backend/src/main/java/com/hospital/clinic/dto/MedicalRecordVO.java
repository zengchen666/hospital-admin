package com.hospital.clinic.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 病历视图对象。
 */
@Getter
@Builder
public class MedicalRecordVO {

    private final String id;

    private final String visitId;

    private final String visitNo;

    private final Long patientId;

    private final String patientName;

    private final Long doctorId;

    private final String doctorName;

    private final Integer visitType;

    private final String chiefComplaint;

    private final String presentIllness;

    private final String physicalExam;

    private final String diagnosis;

    private final String icdCode;

    private final LocalDateTime visitTime;

    /** 0 草稿 / 1 已提交 / 2 已归档 */
    private final Integer status;
}
