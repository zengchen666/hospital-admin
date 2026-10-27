package com.hospital.pharmacy.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商查询。
 */
@Getter
@Setter
public class SupplierQuery extends PageQuery {

    /** 名称 / 编码 / 联系人 模糊 */
    private String keyword;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
