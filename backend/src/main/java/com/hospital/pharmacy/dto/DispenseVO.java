package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发药单视图对象（含明细）。
 */
@Getter
@Builder
public class DispenseVO {

    private final String id;

    private final String dispenseNo;

    private final Long prescId;

    private final Long patientId;

    private final String patientName;

    private final Long warehouseId;

    private final String warehouseName;

    /** 1 门诊发药 / 2 住院发药 / 3 退药 */
    private final Integer dispenseType;

    private final BigDecimal totalAmount;

    private final Long operatorId;

    private final String operatorName;

    private final LocalDateTime dispenseTime;

    /** 0 待发 / 1 已发 / 2 已退 */
    private final Integer status;

    private final List<DispenseItemVO> items;
}
