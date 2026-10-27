package com.hospital.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 报告录入时的单条结果。
 *
 * <p>{@code itemId} 必须是开单时生成的那条明细 —— 用 id 回填而不是按项目编码匹配，
 * 避免同名项目串行、或编码改名后结果对不上。</p>
 */
@Getter
@Setter
public class ExamResultItemRequest {

    @NotNull(message = "明细 ID 不能为空")
    private Long itemId;

    @Size(max = 50, message = "结果值长度不能超过 50")
    private String resultValue;

    @Size(max = 50, message = "参考范围长度不能超过 50")
    private String refRange;

    @Size(max = 20, message = "单位长度不能超过 20")
    private String unit;

    /** 0 正常 / 1 偏高 / 2 偏低 / 3 定性异常 */
    @Min(value = 0, message = "异常标志取值非法")
    @Max(value = 3, message = "异常标志取值非法")
    private Integer abnormalFlag;
}
