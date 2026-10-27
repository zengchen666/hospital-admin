package com.hospital.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 门诊统计结果。
 *
 * <p>「人次」与「人数」是两个指标：<b>人次</b>看工作量（一个人挂三次算三次），
 * <b>人数</b>看服务覆盖（同一个人只算一次）。混用会让复诊率高的科室
 * 看起来比实际忙一倍。</p>
 *
 * <p>本类直接作为聚合 SQL 的返回类型，因此需要无参构造与 setter
 * （MyBatis 按列别名自动映射），不用 final 字段。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutpatientStatVO {

    /** 科室名，为空表示未分科室 */
    private String deptName;

    /** 挂号人次 */
    private Long regCount;

    /** 就诊人数（去重） */
    private Long patientCount;

    /** 挂号费收入 */
    private BigDecimal regFeeAmount;

    /** 趋势视图：统计日期；科室汇总视图为 null */
    private LocalDate statDate;

    /** 该科室人次占全院比例（0~100） */
    private BigDecimal ratio;
}
