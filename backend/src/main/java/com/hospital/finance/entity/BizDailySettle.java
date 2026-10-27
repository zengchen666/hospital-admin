package com.hospital.finance.entity;

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
 * 日结记录 biz_daily_settle。
 *
 * <p>《接口文档》只给了 {@code GET /api/finance/daily} 的汇总口径，没定义落库表。
 * 但「提交日结」这个动作必须有凭据：短款 200 块是谁交的班、当时差异说明写的什么，
 * 第二天查的就是这条记录。只写审计日志不够 —— 审计日志是流水，
 * 查「这个月哪几天有差异」要翻全表，而这里一天一行，直接查。</p>
 *
 * <p>一天只有一条（{@code settle_date} 唯一键）：重复提交是覆盖，不是追加。
 * 同一个人交两次班产生两条日结，账就对不上了。</p>
 */
@Getter
@Setter
@TableName("biz_daily_settle")
public class BizDailySettle extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private LocalDate settleDate;

    private Long chargeCount;

    private BigDecimal totalAmount;

    private Long refundCount;

    private BigDecimal refundAmount;

    /** 应有现金 = 当日现金收入 − 当日现金退费 */
    private BigDecimal expectedCash;

    /** 收费员实点现金 */
    private BigDecimal actualCash;

    /** 差异 = 实点 − 应有，非 0 必须填说明 */
    private BigDecimal diffAmount;

    private String diffRemark;

    private Long operatorId;

    private LocalDateTime submitTime;
}
