package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购单 biz_purchase。
 *
 * <p>状态机：<b>0 草稿 → 1 待审核 → 2 已审核 → 3 已入库</b>，4 已作废。
 * 关键设计：<b>审核通过不直接加库存</b>，必须有独立的「入库」动作。
 * 采购单审核只是「同意买」，货到没到、到多少是另一回事 ——
 * 审核即入库会让库存凭空多出一批还没验收的药。</p>
 */
@Getter
@Setter
@TableName("biz_purchase")
public class BizPurchase extends BaseEntity {

    /** 0 草稿 / 1 待审核 / 2 已审核 / 3 已入库 / 4 已作废 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_AUDITED = 2;
    public static final int STATUS_STOCKED = 3;
    public static final int STATUS_VOID = 4;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String purchaseNo;

    private Long supplierId;

    /** 目标库房 */
    private Long warehouseId;

    private BigDecimal totalAmount;

    private LocalDate expectDate;

    private Integer status;

    private Long applicantId;

    private Long auditorId;

    private LocalDateTime auditTime;

    private String remark;
}
