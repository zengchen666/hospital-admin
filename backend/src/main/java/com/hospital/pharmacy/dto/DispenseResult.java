package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 发药回执。
 */
@Getter
@Builder
public class DispenseResult {

    private final String id;

    private final String dispenseNo;

    /** 实际生成的明细条数：同一药品跨批次会被拆成多条 */
    private final Integer itemCount;

    private final BigDecimal totalAmount;

    /** 1 已发 */
    private final Integer status;
}
