package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品字典 biz_drug。
 *
 * <p>本期只做「读」（开方时选药、取价格快照），药品维护、库存、发药在药房模块补齐。</p>
 */
@Getter
@Setter
@TableName("biz_drug")
public class BizDrug extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String drugCode;

    private String drugName;

    private String genericName;

    private String spec;

    private String dosageForm;

    /** 最小包装单位，如 盒 / 支 */
    private String unit;

    private String manufacturer;

    private String approvalNo;

    private BigDecimal purchasePrice;

    /** 售价，开方取这个价做快照 */
    private BigDecimal salePrice;

    private String category;

    /** 0 否 / 1 麻精毒麻 */
    private Integer isSpecial;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
