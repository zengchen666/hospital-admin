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
 * 医保结算 biz_insurance_settle。
 *
 * <p>一期用 Mock 服务返回固定报文，但表结构按真接口预留：
 * 上送报文 / 返回报文 / 失败原因都落库，将来真对接时排查问题靠的就是这些原文。
 * 只存「结算成功 / 失败」两个状态，排查时等于两眼一抹黑。</p>
 */
@Getter
@Setter
@TableName("biz_insurance_settle")
public class BizInsuranceSettle extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 医保返回的结算单号 */
    private String settleNo;

    private Long chargeId;

    /** 职工 / 居民 / 新农合 / 商保 */
    private String insuranceType;

    private BigDecimal totalAmount;

    private BigDecimal insuranceAmount;

    private BigDecimal selfAmount;

    /** 0 待结算 / 1 已结算 / 2 结算失败 */
    private Integer settleStatus;

    private String failReason;

    private String requestBody;

    private String responseBody;

    private LocalDateTime settleTime;
}
