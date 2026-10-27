package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 出入库流水 biz_stock_record。
 *
 * <p><b>每一次库存变动都必须留痕</b>，且与 {@link BizStock} 的扣减在同一事务里。
 * 没有流水的库存变动等于账做不平 —— 出问题时连查都无从查起。</p>
 *
 * <p><b>为什么不像其他实体那样继承 BaseEntity</b>：流水表刻意只有
 * {@code create_time}，没有 {@code update_by / update_time / deleted / version} ——
 * 账目流水写下去就不允许改、不允许删。硬套基类会拼出不存在的列直接报 SQL 错。</p>
 */
@Getter
@Setter
@TableName("biz_stock_record")
public class BizStockRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long drugId;

    private Long warehouseId;

    /** 1 入库 / 2 出库 / 3 盘点 / 4 退货 */
    private Integer recordType;

    /** 变动数量（正数，方向由 recordType 决定） */
    private Integer quantity;

    private String batchNo;

    /** 来源单号：采购单号 / 发药单号 / 盘点单号 */
    private String relatedNo;

    private Long operatorId;

    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
