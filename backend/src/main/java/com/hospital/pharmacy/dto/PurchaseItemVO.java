package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 采购明细视图对象。
 */
@Getter
@Builder
public class PurchaseItemVO {

    private final String id;

    private final String drugId;

    private final String drugCode;

    private final String drugName;

    private final String spec;

    private final String unit;

    /** 采购数量 */
    private final Integer quantity;

    /** 采购单价 */
    private final BigDecimal price;

    private final BigDecimal amount;

    /** 实到数量，未入库为 0 */
    private final Integer arriveNum;
}
