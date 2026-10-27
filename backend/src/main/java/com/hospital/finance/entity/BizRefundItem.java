package com.hospital.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 退费明细 biz_refund_item。
 *
 * <p>记录「退的是哪一项、退了几件」。退款执行时按这里的
 * {@code charge_item_id} 逐条回冲 {@code biz_charge_item.refund_num}，
 * 并按原批号把库存加回去 —— 没有明细这两件事都做不了。</p>
 */
@Getter
@Setter
@TableName("biz_refund_item")
public class BizRefundItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long refundId;

    private Long chargeItemId;

    /** 1 药品 / 2 诊疗 / 3 检查 / 4 床位 */
    private Integer itemType;

    private String itemName;

    private Integer quantity;

    private BigDecimal amount;
}
