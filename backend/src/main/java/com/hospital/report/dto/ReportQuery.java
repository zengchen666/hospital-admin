package com.hospital.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 报表查询条件。
 *
 * <p><b>日期范围必须收敛</b>：默认近 30 天，硬上限 {@value #MAX_RANGE_DAYS} 天。
 * 报表是后台最容易把主库拖垮的功能 —— 一个不带时间条件的 {@code GROUP BY}
 * 就能让 CPU 打满，然后整院挂号一起变慢。宁可让使用者多查几次，
 * 也不能让他一次把库查死。</p>
 */
@Getter
@Setter
public class ReportQuery {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 366;

    /** 起始日期（含），为空时默认近 30 天 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startTime;

    /** 结束日期（含），为空时默认今天 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endTime;

    private Long deptId;

    private Long doctorId;

    /** 排行榜返回条数 */
    @Min(value = 1, message = "topN 至少为 1")
    @Max(value = 100, message = "topN 不能超过 100")
    private int topN = 20;
}
