package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 库存视图对象（药品 + 库房 + 批号 一行）。
 */
@Getter
@Builder
public class StockVO {

    private final String id;

    private final String drugId;

    private final String drugCode;

    private final String drugName;

    private final String spec;

    private final String unit;

    private final String warehouseId;

    private final String warehouseName;

    private final String batchNo;

    private final Integer quantity;

    private final Integer warnNum;

    private final LocalDate produceDate;

    private final LocalDate expireDate;

    /** quantity <= warnNum */
    private final boolean warn;

    /** 已过期 或 剩余天数 <= expireDays（默认按 90 天算） */
    private final boolean expiring;

    /** 距离失效还有多少天，负数表示已过期 */
    private final Long daysToExpire;
}
