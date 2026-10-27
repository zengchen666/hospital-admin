package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 药品视图对象。
 *
 * <p>带 {@code totalStock} 是为了让库存页能直接显示「这个药总共还剩多少」，
 * 不必前端拿药品 ID 再查一次库存接口。库存是跨库房汇总值，
 * 只用于展示，<b>不能用于扣减</b> —— 扣减必须落到具体库房 + 批号。</p>
 */
@Getter
@Builder
public class DrugVO {

    private final String id;

    private final String drugCode;

    private final String drugName;

    private final String genericName;

    private final String spec;

    private final String dosageForm;

    private final String unit;

    private final String manufacturer;

    private final String approvalNo;

    private final BigDecimal purchasePrice;

    private final BigDecimal salePrice;

    private final String category;

    /** 0 否 / 1 麻精毒麻 */
    private final Integer isSpecial;

    /** 0 停用 / 1 启用 */
    private final Integer status;

    /** 跨库房库存合计（仅展示用） */
    private final Integer totalStock;
}
