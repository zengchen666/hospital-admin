package com.hospital.report.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 自定义报表配置视图。
 *
 * <p>{@code builtin} 区分「系统内置的快照报表」与「用户自己拖出来的报表」：
 * 内置的由后台首次生成快照时自动创建（{@code creatorId} 为空），
 * 不允许在界面上删除 —— 删了下次生成快照会重建一条，用户看到的是
 * 「删了又冒出来」的灵异现象。</p>
 */
@Getter
@Setter
public class ReportConfigVO {

    private Long id;

    private String reportName;

    /** 数据集编码，见 {@link com.hospital.report.service.ReportType} */
    private String reportType;

    /** 数据集中文名，前端免维护一份字典 */
    private String reportTypeName;

    /** 统计维度编码，见 {@link com.hospital.report.service.ReportDimension} */
    private String dimension;

    /** 统计维度中文名 */
    private String dimensionName;

    private String chartType;

    private String params;

    private String configJson;

    /** 创建人姓名 */
    private String creatorName;

    /** true 表示系统内置，不可删除 */
    private Boolean builtin;

    private LocalDateTime createTime;
}
