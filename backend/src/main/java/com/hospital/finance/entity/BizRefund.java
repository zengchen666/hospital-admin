package com.hospital.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退费申请 biz_refund。
 *
 * <p>状态机：<b>0 待审核 → 1 已审核 → 2 已退款</b>，或任意前置状态 → 3 已驳回。
 * 之所以要「申请」和「审核」两步，是因为退费是医院里最容易出内部问题的操作：
 * 收钱和退钱如果不是两个人，长年累月一定会出问题。</p>
 *
 * <p>金额以<b>明细汇总</b>为准，不单独信 {@code refund_amount}：
 * 两者对不上就是数据被改过，宁可拒绝也不能退错钱。</p>
 */
@Getter
@Setter
@TableName("biz_refund")
public class BizRefund extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String refundNo;

    private Long chargeId;

    private Long patientId;

    private BigDecimal refundAmount;

    /** 1 全额 / 2 部分 */
    private Integer refundType;

    private String reason;

    /** 0 待审核 / 1 已审核 / 2 已退款 / 3 已驳回 */
    private Integer status;

    private Long applicantId;

    private Long auditorId;

    private LocalDateTime auditTime;

    private LocalDateTime refundTime;

    /** 原支付方式，原路退回的依据 */
    private Integer originalPay;
}
