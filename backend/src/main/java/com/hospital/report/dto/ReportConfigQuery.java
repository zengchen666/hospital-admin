package com.hospital.report.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 自定义报表配置分页查询。
 *
 * <p>只按数据集过滤：报表配置量级很小（全院几十份），
 * 再堆关键字模糊查询属于过度设计。</p>
 */
@Getter
@Setter
public class ReportConfigQuery extends PageQuery {

    /** 数据集编码，为空表示全部 */
    private String reportType;
}
