package com.hospital.report.service;

import com.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统计维度白名单矩阵。
 *
 * <p>这张矩阵是自定义报表的<b>全部攻击面</b>：只要有一个组合漏进来，
 * 就等价于开放了一个未被评审的数据视角。因此每个组合都要有断言，
 * 而不是只测「正常路径能过」。</p>
 */
class ReportDimensionTest {

    @Test
    @DisplayName("非法维度一律 40001，不进入任何分支")
    void unknownDimensionIsRejected() {
        assertThatThrownBy(() -> ReportDimension.of("deptId; DROP TABLE biz_charge"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计维度非法");

        assertThatThrownBy(() -> ReportDimension.of(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计维度不能为空");
    }

    @Test
    @DisplayName("数据集与维度不匹配时 40001，并给出该数据集的可选维度")
    void mismatchedCombinationIsRejected() {
        // 药品统计没有「科室」维度的聚合口径
        assertThatThrownBy(() -> ReportDimension.match(ReportType.DRUG, "dept"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持维度 dept");

        // 门诊统计没有「病种」维度
        assertThatThrownBy(() -> ReportDimension.match(ReportType.OUTPATIENT, "disease"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持维度 disease");
    }

    @Test
    @DisplayName("合法组合放行，并返回归一化后的维度")
    void validCombinationIsAccepted() {
        assertThat(ReportDimension.match(ReportType.OUTPATIENT, "dept")).isEqualTo(ReportDimension.DEPT);
        assertThat(ReportDimension.match(ReportType.INCOME, "doctor")).isEqualTo(ReportDimension.DOCTOR);
        assertThat(ReportDimension.match(ReportType.PATIENT, "AGE")).isEqualTo(ReportDimension.AGE);
        assertThat(ReportDimension.match(ReportType.INPATIENT, "summary")).isEqualTo(ReportDimension.SUMMARY);
    }

    @Test
    @DisplayName("维度为空时取该数据集的第一个合法维度，少一次前后端往返")
    void blankDimensionFallsBackToFirstSupported() {
        assertThat(ReportDimension.match(ReportType.OUTPATIENT, null)).isEqualTo(ReportDimension.DEPT);
        assertThat(ReportDimension.match(ReportType.PATIENT, "  ")).isEqualTo(ReportDimension.AGE);
        assertThat(ReportDimension.match(ReportType.DRUG, null)).isEqualTo(ReportDimension.DRUG);
    }

    @Test
    @DisplayName("住院概况只有汇总维度，不存在可分组维度")
    void inpatientHasOnlySummary() {
        assertThat(ReportDimension.dimensionsOf(ReportType.INPATIENT))
                .containsExactly("summary");
    }

    @Test
    @DisplayName("每个数据集至少有一个可用维度，否则配置页会出现死选项")
    void everyTypeHasAtLeastOneDimension() {
        for (ReportType type : ReportType.values()) {
            assertThat(ReportDimension.dimensionsOf(type))
                    .as("数据集 %s 的可用维度", type.getCode())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("展示形式走白名单，空值默认表格")
    void chartTypeIsWhitelisted() {
        assertThat(ReportDimension.normalizeChartType(null)).isEqualTo("table");
        assertThat(ReportDimension.normalizeChartType(" BAR ")).isEqualTo("bar");
        assertThat(ReportDimension.normalizeChartType("pie")).isEqualTo("pie");

        assertThatThrownBy(() -> ReportDimension.normalizeChartType("chart.js"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("展示形式非法");
    }
}
