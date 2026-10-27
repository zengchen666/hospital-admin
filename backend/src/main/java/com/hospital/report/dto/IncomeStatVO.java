package com.hospital.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收入统计结果。
 *
 * <p><b>只统计已支付（{@code pay_status = 1}）的收费单</b>。把未支付的单子算进收入，
 * 报表当天好看，月底对账时全是窟窿。</p>
 *
 * <p>{@code dimension} 区分本 VO 承载的三种视角：
 * {@code ITEM_TYPE} 收入构成 / {@code DEPT} 科室收入 / {@code DOCTOR} 医生绩效。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatVO {

    /** 统计维度：ITEM_TYPE / DEPT / DOCTOR */
    private String dimension;

    /** 维度键：项目类型码 / 科室 ID / 医生 ID */
    private String dimensionKey;

    /** 维度名称：项目类型名 / 科室名 / 医生姓名 */
    private String dimensionName;

    /** 收入金额 */
    private BigDecimal amount;

    /** 单据数 */
    private Long chargeCount;

    /** 项目/药品数量，医生绩效维度为 null */
    private Long quantity;

    /** 接诊人次，仅医生绩效维度有值 */
    private Long visitCount;

    /** 开方金额，仅医生绩效维度有值 */
    private BigDecimal prescAmount;
}
