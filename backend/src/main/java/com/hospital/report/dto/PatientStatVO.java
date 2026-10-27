package com.hospital.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者分析结果。
 *
 * <p>{@code dimension} 区分四种视角：
 * {@code AGE} 年龄分布 / {@code GENDER} 性别分布 / {@code DISEASE} 病种分布 / {@code NEW} 建档趋势。</p>
 *
 * <p>年龄按 {@code TIMESTAMPDIFF(YEAR, birth_date, CURDATE())} 实时算，
 * 不存「年龄」字段 —— 存下来的年龄第二天就开始错。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientStatVO {

    /** 统计维度：AGE / GENDER / DISEASE / NEW */
    private String dimension;

    /** 维度名：年龄段 / 性别码 / ICD 编码 */
    private String dimensionName;

    /** 展示名：如「18-39岁」「男」「急性上呼吸道感染」 */
    private String displayName;

    /** 患者数 / 就诊人次 */
    private Long patientCount;

    /** 建档趋势视图：统计月份第一天 */
    private LocalDate statMonth;
}
