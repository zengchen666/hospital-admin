package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 收费明细视图对象。
 */
@Getter
@Builder
public class ChargeItemVO {

    private final String id;

    /** 1 药品 / 2 诊疗 / 3 检查 / 4 床位 */
    private final Integer itemType;

    private final Long itemId;

    private final String itemName;

    private final Integer quantity;

    private final BigDecimal price;

    private final BigDecimal amount;

    private final Integer refundNum;
}
