package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 发药明细 biz_dispense_item。
 *
 * <p>{@code batch_no} 必填：库存是按批号管理的，不写批号就没法扣减，
 * 也没法在召回某批次药品时定位到具体发了哪些人。</p>
 */
@Getter
@Setter
@TableName("biz_dispense_item")
public class BizDispenseItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long dispenseId;

    private Long drugId;

    private String batchNo;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal amount;
}
