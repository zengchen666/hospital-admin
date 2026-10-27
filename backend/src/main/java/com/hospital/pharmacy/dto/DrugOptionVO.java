package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 药品选项（开方选药用）。
 *
 * <p>只暴露选药必需字段，不含进价等内部数据 —— 进价泄露等于把毛利告诉所有人。</p>
 */
@Getter
@Builder
public class DrugOptionVO {

    private final String id;

    private final String drugCode;

    private final String drugName;

    private final String spec;

    private final String unit;

    private final BigDecimal salePrice;

    private final String category;

    /** 是否麻精毒麻药品，前端需要额外提示（如开具权限） */
    private final Integer isSpecial;
}
