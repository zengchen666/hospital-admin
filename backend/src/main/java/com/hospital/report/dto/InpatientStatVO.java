package com.hospital.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 住院统计结果。
 *
 * <p>两个口径容易算错，这里特意在字段名上写死：
 * <ul>
 *   <li><b>床位使用率的分母是「可用床位」</b>（排除维修床）。把维修床算进分母，
 *       使用率会偏低，正好掩盖床位紧张 —— 这是最危险的一种算错，因为
 *       它让人放心。</li>
 *   <li><b>平均住院日只统计已出院患者</b>。在院患者的天数还在涨，
 *       算进去会系统性低估平均住院日。</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InpatientStatVO {

    /** 当前在院人数 */
    private Long inHospitalCount;

    /** 区间内入院人次 */
    private Long admittedCount;

    /** 区间内出院人次 */
    private Long dischargedCount;

    /** 可用床位总数（排除维修） */
    private Long availableBeds;

    /** 已占用床位 */
    private Long occupiedBeds;

    /** 床位使用率（0~100，两位小数） */
    private BigDecimal bedUsageRate;

    /** 平均住院日（只算出院，入院当天计 1 天） */
    private BigDecimal avgStayDays;

    /** 在院患者预交金余额合计 */
    private BigDecimal depositBalance;
}
