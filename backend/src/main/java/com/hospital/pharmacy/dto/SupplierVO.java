package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 供应商视图对象。
 */
@Getter
@Builder
public class SupplierVO {

    private final String id;

    private final String supplierName;

    private final String supplierCode;

    private final String contactName;

    private final String contactPhone;

    private final String address;

    private final String licenseNo;

    /** 信用等级 1-5，5 最优 */
    private final Integer creditLevel;

    /** 0 停用 / 1 启用 */
    private final Integer status;
}
