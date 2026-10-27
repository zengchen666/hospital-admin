package com.hospital.publichealth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 传染病报告卡视图对象。
 *
 * <p>{@code overdueHours} 是给管理者看的「超期未上报小时数」。
 * 法定时限按病种分类（甲类及按甲类管理 2 小时、乙类 24 小时），
 * 一期没有病种字典，这里统一按 <b>24 小时</b> 核算并在注释里标明口径，
 * 后续接入病种字典后按病种取值。</p>
 */
@Getter
@Setter
@Builder
public class InfectiousVO {

    private String id;
    private Long patientId;
    private String patientName;
    private String diseaseName;
    private String icdCode;
    private LocalDate reportDate;
    private Long reporterId;
    private String reporterName;
    private Integer reportStatus;
    private String cdcResponse;

    /** 填卡时间，法定时限的起算近似值（真实口径应为诊断时间，一期无该字段） */
    private java.time.LocalDateTime createTime;

    /** 待上报且已超过 24 小时的小时数；已上报或时限内为 0 */
    private Long overdueHours;
}
