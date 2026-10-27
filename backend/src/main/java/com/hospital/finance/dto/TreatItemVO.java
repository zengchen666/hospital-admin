package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 诊疗项目（价目表）视图对象。
 */
@Getter
@Builder
public class TreatItemVO {

    private final String id;

    private final String itemCode;

    private final String itemName;

    /** 1 诊疗 / 2 检查 / 3 检验 / 4 床位 / 5 手术 / 6 材料 */
    private final Integer itemType;

    private final String spec;

    private final String unit;

    private final BigDecimal price;

    /** 1 甲类 / 2 乙类 / 3 丙类（自费） */
    private final Integer insuranceType;

    private final BigDecimal insuranceRatio;

    private final Long deptId;

    private final String deptName;

    /** 0 停用 / 1 启用 */
    private final Integer status;

    private final LocalDate effectiveDate;

    /** true 表示同一编码下还有别的版本（历史价或未来生效价） */
    private final Boolean hasHistory;
}
