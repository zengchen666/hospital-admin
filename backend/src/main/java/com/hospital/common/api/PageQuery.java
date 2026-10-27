package com.hospital.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 分页查询基类，对应文档 1.1 的分页参数。
 *
 * <p>各模块的分页查询 DTO 继承本类，不要重复定义 pageNum/pageSize。</p>
 */
@Getter
@Setter
public class PageQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private long pageNum = 1L;

    @Min(value = 1, message = "每页条数至少为 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private long pageSize = 10L;

    /** 模糊搜索关键字 */
    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endTime;

    /** 排序字段，仅允许白名单字段，Service 层需校验，防注入 */
    private String sortField;

    /** asc / desc */
    private String sortOrder;
}
