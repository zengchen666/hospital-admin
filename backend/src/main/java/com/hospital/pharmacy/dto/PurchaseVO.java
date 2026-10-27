package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购单视图对象（含明细）。
 */
@Getter
@Builder
public class PurchaseVO {

    private final String id;

    private final String purchaseNo;

    private final String supplierId;

    private final String supplierName;

    private final String warehouseId;

    private final String warehouseName;

    private final BigDecimal totalAmount;

    private final LocalDate expectDate;

    /** 0 草稿 / 1 待审核 / 2 已审核 / 3 已入库 / 4 已作废 */
    private final Integer status;

    private final String applicantId;

    private final String applicantName;

    private final String auditorId;

    private final String auditorName;

    private final LocalDateTime auditTime;

    private final String remark;

    private final LocalDateTime createTime;

    private final List<PurchaseItemVO> items;
}
