package com.hospital.exam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 单项结果保存（批量接口的行）。
 */
@Getter
@Setter
public class ExamItemResultRequest {

    @NotNull(message = "明细 ID 不能为空")
    private Long itemId;

    /** 结果值，数值或定性（如 12.5 / 阴性） */
    @Size(max = 50, message = "结果值长度不能超过 50")
    private String resultValue;

    /** 参考范围，如 4-10 */
    @Size(max = 50, message = "参考范围长度不能超过 50")
    private String refRange;

    @Size(max = 20, message = "单位长度不能超过 20")
    private String unit;

    /** 0 正常 / 1 偏高 / 2 偏低 / 3 定性异常 */
    private Integer abnormalFlag;
}
