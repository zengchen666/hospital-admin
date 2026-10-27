package com.hospital.exam.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 检查项目视图对象（含结果）。
 */
@Getter
@Builder
public class ExamItemVO {

    private final String id;

    private final String itemCode;

    private final String itemName;

    private final String resultValue;

    private final String refRange;

    private final String unit;

    /** 0 正常 / 1 偏高 / 2 偏低 / 3 定性异常 */
    private final Integer abnormalFlag;

    private final Integer sort;
}
