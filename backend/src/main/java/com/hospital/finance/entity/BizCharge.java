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
 * 收费单 biz_charge。
 *
 * <p><b>来源单关联</b>：{@code source_type + source_id} 上有唯一索引
 * （{@code uk_charge_source}），同一张处方重复收费会在数据库层被拦下 ——
 * 这比在 Service 里「先查有没有收过」可靠得多，因为并发下查与写之间永远有窗口。</p>
 *
 * <p><b>金额三兄弟</b>：{@code total_amount}（明细汇总）、{@code discount_amount}（优惠）、
 * {@code actual_amount}（实收）= 前两者之差。三者必须自洽，
 * 否则日结对不上账。</p>
 */
@Getter
@Setter
@TableName("biz_charge")
public class BizCharge extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String chargeNo;

    private Long patientId;

    private Long visitId;

    /** 0 无 / 1 处方 / 2 检查 / 3 挂号 / 4 住院 */
    private Integer sourceType;

    /** 来源单 ID，配合 sourceType 唯一 */
    private Long sourceId;

    /** 1 挂号 / 2 门诊 / 3 住院 */
    private Integer chargeType;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal actualAmount;

    /** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
    private Integer payType;

    /** 0 待支付 / 1 已支付 / 2 已退费 / 3 部分退费 */
    private Integer payStatus;

    private Long operatorId;

    private LocalDateTime chargeTime;
}
