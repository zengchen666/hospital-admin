package com.hospital.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 报表配置 sys_report_config。
 *
 * <p><b>刻意不存可执行 SQL</b>：只存「数据集 + 维度 + 指标 + 过滤」的 JSON，
 * SQL 由后端按固定模板拼装。存 SQL 等于给有后台权限的人开了一个绕过所有
 * 数据权限的查询入口 —— 那是把越权和注入一起打包送出去。</p>
 */
@Getter
@Setter
@TableName("sys_report_config")
public class SysReportConfig extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String reportName;

    /** 报表类型（数据集），取值见 {@link com.hospital.report.service.ReportType} */
    private String reportType;

    /**
     * 统计维度，取值见 {@link com.hospital.report.service.ReportDimension}。
     *
     * <p>维度单独成列，不塞进 {@code configJson}：它是查询路径的一部分，
     * 需要被 SQL 条件命中。埋在 JSON 里就只能全表扫出来再在 Java 里过滤。</p>
     */
    private String dimension;

    /** 展示形式：table / bar / line / pie */
    private String chartType;

    /** 查询配置 JSON：{metrics, sort, filters}，供前端还原拖拽画布 */
    private String configJson;

    /** 可配参数定义（时间范围、科室等） */
    private String params;

    private Long creatorId;
}
