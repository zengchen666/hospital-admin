package com.hospital.clinic.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 接诊结果：医生点「接诊」后拿到的工作台首屏数据。
 *
 * <p>刻意把<b>过敏史</b>放进来：开方前最该看的就是这一项，
 * 让前端不必再单独查一次患者详情，也避免医生漏看。</p>
 */
@Getter
@Builder
public class ReceiveResult {

    /** 就诊 ID（一期 = biz_registration.id），后续病历、处方都用它关联 */
    private final String visitId;

    private final String regNo;

    private final Integer queueNo;

    private final Long patientId;

    private final String patientNo;

    private final String patientName;

    private final Integer gender;

    private final Integer age;

    /** 过敏史，开方前必看 */
    private final String allergyHistory;

    /** 挂号状态：0 待就诊 / 1 就诊中 / 2 已完成 / 3 已退号 */
    private final Integer visitStatus;

    /** 已存在的病历 ID（首次接诊为 null） */
    private final String recordId;

    /** 病历状态：0 草稿 / 1 已提交 / 2 已归档（无病历时为 null） */
    private final Integer recordStatus;
}
