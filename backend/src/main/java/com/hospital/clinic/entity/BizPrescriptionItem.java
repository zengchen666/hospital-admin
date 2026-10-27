package com.hospital.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处方明细 biz_prescription_item。
 *
 * <p>{@code drug_name} / {@code spec} / {@code price} 都是<b>快照</b>：
 * 药品后续改了名或调了价，历史处方必须保持当时的样子，否则退费、对账全对不上。</p>
 */
@Getter
@Setter
@TableName("biz_prescription_item")
public class BizPrescriptionItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long prescId;

    private Long drugId;

    /** 药品名称快照 */
    private String drugName;

    /** 规格快照 */
    private String spec;

    /** 用法，如「口服」。usage 在 MySQL 里属非保留关键字，仍加反引号避免歧义 */
    @TableField("`usage`")
    private String usage;

    /** 单次用量 */
    private String dosage;

    /** 频次，如「每日 3 次」 */
    private String frequency;

    /** 用药天数 */
    private Integer days;

    private Integer quantity;

    /** 单价快照 */
    private BigDecimal price;

    /** 小计 = 单价 × 数量 */
    private BigDecimal amount;
}
