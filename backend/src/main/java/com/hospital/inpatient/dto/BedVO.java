package com.hospital.inpatient.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 床位视图对象（床位图用）。
 */
@Getter
@Builder
public class BedVO {

    private final String id;

    private final Long wardId;

    private final String wardName;

    private final String bedNo;

    /** 1 普通 / 2 重症 / 3 VIP */
    private final Integer bedType;

    private final BigDecimal price;

    /** 0 空闲 / 1 占用 / 2 维修 */
    private final Integer status;

    /** 当前占用该床的患者姓名（空闲时为 null） */
    private final String occupantName;
}
