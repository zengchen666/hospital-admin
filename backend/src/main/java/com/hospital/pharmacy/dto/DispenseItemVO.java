package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 发药明细视图对象。
 */
@Getter
@Builder
public class DispenseItemVO {

    private final String id;

    private final Long drugId;

    private final String drugName;

    /** 批号，药品召回时可据此定位到人 */
    private final String batchNo;

    private final Integer quantity;

    private final BigDecimal price;

    private final BigDecimal amount;
}
