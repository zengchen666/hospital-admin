package com.hospital.patient.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者视图对象。
 *
 * <p><b>身份证与手机号默认脱敏</b>；{@code medicalHistory} / {@code familyHistory}
 * 只在详情接口返回，列表接口留空（既省带宽，也避免病史在列表页被随意浏览）。</p>
 */
@Getter
@Builder
public class PatientVO {

    /** 雪花 ID 转字符串，防前端精度丢失 */
    private final String id;

    private final String patientNo;

    private final String name;

    private final Integer gender;

    /** 由出生日期实时计算，不落库（避免「年龄」这种会变的字段需要天天刷库） */
    private final Integer age;

    private final LocalDate birthDate;

    /** 脱敏：4403**********1234 */
    private final String idCard;

    /** 脱敏：138****0000 */
    private final String phone;

    private final String address;

    private final String bloodType;

    private final String allergyHistory;

    private final String medicalHistory;

    private final String familyHistory;

    private final String cardNo;

    private final BigDecimal cardBalance;

    private final String tags;

    private final LocalDateTime createTime;
}
