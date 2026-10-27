package com.hospital.pharmacy.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 采购单查询。
 */
@Getter
@Setter
public class PurchaseQuery extends PageQuery {

    /** 采购单号模糊 */
    private String purchaseNo;

    private Long supplierId;

    private Long warehouseId;

    /** 0 草稿 / 1 待审核 / 2 已审核 / 3 已入库 / 4 已作废 */
    private Integer status;
}
