package com.hospital.inpatient.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 病区视图对象（下拉框用）。
 */
@Getter
@Builder
public class WardVO {

    private final Long id;

    private final String wardName;

    private final String wardCode;

    private final Long deptId;

    private final String floor;

    private final Integer bedCount;

    /** 0 停用 / 1 启用 */
    private final Integer status;
}
