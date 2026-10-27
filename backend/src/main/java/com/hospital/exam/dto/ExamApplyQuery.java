package com.hospital.exam.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查单查询条件。
 */
@Getter
@Setter
public class ExamApplyQuery extends PageQuery {

    private Long patientId;

    private Long visitId;

    private Long doctorId;

    /** 1 检验 / 2 检查（影像） */
    private Integer examType;

    /** 0 待缴费 / 1 待采样 / 2 检验中 / 3 已出报告 */
    private Integer status;

    /** true 时只看危急值已发布的报告（临床待处理） */
    private Boolean criticalOnly;
}
