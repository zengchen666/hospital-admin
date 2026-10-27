package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 退费明细视图对象。
 */
@Getter
@Builder
public class RefundItemVO {

    private final String id;

    private final String chargeItemId;

    /** 1 药品 / 2 诊疗 / 3 检查 / 4 床位 */
    private final Integer itemType;

    private final String itemName;

    private final Integer quantity;

    private final BigDecimal amount;
}
