package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 药品库存 biz_stock（按 药品 + 库房 + 批号 唯一）。
 *
 * <p><b>扣减只能走条件更新</b>：{@code quantity = quantity - n WHERE quantity >= n}。
 * 先查余量再改，在发药窗口排队时必然出现负库存。</p>
 */
@Getter
@Setter
@TableName("biz_stock")
public class BizStock extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long drugId;

    private Long warehouseId;

    private String batchNo;

    private Integer quantity;

    /** 预警数量，低于此值触发提醒 */
    private Integer warnNum;

    private LocalDate produceDate;

    /** 有效期至，发药按此升序（近效期先出） */
    private LocalDate expireDate;
}
