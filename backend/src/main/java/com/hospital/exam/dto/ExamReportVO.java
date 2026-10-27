package com.hospital.exam.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验报告视图对象。
 */
@Getter
@Builder
public class ExamReportVO {

    private final String id;

    private final String applyId;

    private final String applyNo;

    private final String reportNo;

    private final Long patientId;

    private final String patientName;

    private final String result;

    private final String conclusion;

    /** 0 否 / 1 是 */
    private final Integer isCritical;

    private final Long reporterId;

    private final String reporterName;

    private final Long auditorId;

    private final String auditorName;

    private final LocalDateTime reportTime;

    /** 0 草稿 / 1 已发布 */
    private final Integer status;

    private final List<ExamItemVO> items;

    /** 危急值处理状态：0 未处理 / 1 已处理 */
    private final Integer handleStatus;

    private final String handlerName;

    private final LocalDateTime handleTime;

    private final String handleRemark;
}
