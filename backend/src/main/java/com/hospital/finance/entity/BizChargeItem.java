package com.hospital.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费明细 biz_charge_item。
 *
 * <p>项目名称与单价都是快照：药品调价、诊疗项目改名都不能影响已收费的历史单据。</p>
 */
@Getter
@Setter
@TableName("biz_charge_item")
public class BizChargeItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long chargeId;

    /** 1 药品 / 2 诊疗 / 3 检查 / 4 床位 */
    private Integer itemType;

    /** 来源项目 ID（药品 ID 等），便于溯源 */
    private Long itemId;

    private String itemName;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal amount;

    /** 已退数量，不能超过 quantity */
    private Integer refundNum;
}
