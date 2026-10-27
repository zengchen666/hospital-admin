package com.hospital.pharmacy.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品字典查询。
 */
@Getter
@Setter
public class DrugQuery extends PageQuery {

    /** 药品编码 / 名称 / 通用名 模糊匹配 */
    private String keyword;

    private String category;

    /** 0 停用 / 1 启用 */
    private Integer status;

    /** 只看麻精等特殊药品 */
    private Boolean onlySpecial;
}
