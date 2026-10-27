package com.hospital.exam.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检查单视图对象（含明细与报告状态摘要）。
 */
@Getter
@Builder
public class ExamApplyVO {

    private final String id;

    private final String applyNo;

    private final Long patientId;

    private final String patientName;

    private final Long visitId;

    private final Long doctorId;

    private final String doctorName;

    /** 1 检验 / 2 检查（影像） */
    private final Integer examType;

    private final String examItem;

    private final String bodyPart;

    private final String clinicalDiagnosis;

    /** 0 待缴费 / 1 待采样 / 2 检验中 / 3 已出报告 */
    private final Integer status;

    /** 样本条码号（采样后才有，未采样为 null） */
    private final String barcodeNo;

    private final LocalDateTime applyTime;

    private final List<ExamItemVO> items;

    /** 报告 ID（未出报告为 null） */
    private final String reportId;

    /** 报告状态：0 草稿 / 1 已发布（无报告为 null） */
    private final Integer reportStatus;

    /** 是否危急值：0 否 / 1 是（无报告为 null） */
    private final Integer isCritical;
}
