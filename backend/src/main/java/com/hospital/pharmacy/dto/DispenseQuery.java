package com.hospital.pharmacy.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 发药单查询条件。
 */
@Getter
@Setter
public class DispenseQuery extends PageQuery {

    private Long prescId;

    private Long patientId;

    private Long warehouseId;

    /** 0 待发 / 1 已发 / 2 已退 */
    private Integer status;

    private Integer dispenseType;
}
