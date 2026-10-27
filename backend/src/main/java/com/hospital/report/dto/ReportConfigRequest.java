package com.hospital.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 自定义报表配置入参。
 *
 * <p><b>这里刻意不接收 SQL、不接收表名、不接收列名</b>。
 * 「拖拽式配置」在前端是自由拖拽，落到后端必须是<b>有限集合里的选择</b>：
 * 数据集（{@code reportType}）× 统计维度（{@code dimension}）走白名单矩阵，
 * 指标由后端按数据集固定。放开任意一项，就等于给每个能配报表的人
 * 发了一张绕过数据权限的查询通行证。</p>
 */
@Getter
@Setter
public class ReportConfigRequest {

    /** 报表名称 */
    @NotBlank(message = "报表名称不能为空")
    @Size(max = 100, message = "报表名称不能超过 100 个字符")
    private String reportName;

    /** 数据集，取值见 {@link com.hospital.report.service.ReportType} */
    @NotBlank(message = "数据集不能为空")
    private String reportType;

    /** 统计维度，取值见 {@link com.hospital.report.service.ReportDimension} */
    @NotBlank(message = "统计维度不能为空")
    private String dimension;

    /** 展示形式：table / bar / line / pie */
    @Size(max = 20, message = "展示形式不能超过 20 个字符")
    private String chartType = "table";

    /**
     * 默认参数 JSON（时间范围、科室、TopN 等）。
     *
     * <p>存的是「这份报表默认怎么查」，不是查询条件本身；
     * 实际查询参数仍由调用方在 execute 时传入并重新校验。</p>
     */
    @Size(max = 500, message = "默认参数不能超过 500 个字符")
    private String params;

    /** 配置明细 JSON：选中的指标列、排序等，供前端还原拖拽画布 */
    @Size(max = 2000, message = "配置明细过长")
    private String configJson;
}
