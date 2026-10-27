package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 采购明细 biz_purchase_item。
 *
 * <p>{@code amount} 落库而不每次算：采购价会随批次变动，
 * 明细上的金额是当时谈定的价格，改了药品字典里的进价也不该追溯改写历史单据。</p>
 *
 * <p>{@code arriveNum} 是实到数量，与 {@code quantity} 分开记 ——
 * 供应商少发、破损拒收都靠这两个数的差额体现。</p>
 */
@Getter
@Setter
@TableName("biz_purchase_item")
public class BizPurchaseItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long purchaseId;

    private Long drugId;

    /** 采购数量 */
    private Integer quantity;

    /** 采购单价 */
    private BigDecimal price;

    /** 小计 = quantity * price */
    private BigDecimal amount;

    /** 实到数量，入库时回填 */
    private Integer arriveNum;
}
