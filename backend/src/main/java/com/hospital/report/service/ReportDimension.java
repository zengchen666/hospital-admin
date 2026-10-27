package com.hospital.report.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统计维度白名单，以及「数据集 × 维度」的合法组合矩阵。
 *
 * <p><b>为什么要有这张矩阵</b>：自定义报表最容易做成
 * 「前端传维度名，后端拼进 GROUP BY」。只要维度名能被外部控制，
 * 就等价于开放了任意列查询 —— 报表权限立刻升级成全库读权限。
 * 这里把每一对「数据集 + 维度」显式列出来，不在表里的组合一律 40001。</p>
 *
 * <p>新增一种交叉分析时，<b>必须</b>先在这里登记，再写聚合 SQL。
 * 登记处就是评审点：评审者只需要看这张表，就知道系统到底开放了哪些数据视角。</p>
 */
@Getter
public enum ReportDimension {

    DEPT("dept", "科室", ReportType.OUTPATIENT, ReportType.INCOME),
    DATE("date", "日期", ReportType.OUTPATIENT),
    ITEM_TYPE("itemType", "项目类型", ReportType.INCOME),
    DOCTOR("doctor", "医生", ReportType.INCOME),
    DRUG("drug", "药品", ReportType.DRUG),
    AGE("age", "年龄段", ReportType.PATIENT),
    GENDER("gender", "性别", ReportType.PATIENT),
    DISEASE("disease", "病种", ReportType.PATIENT),
    NEW_TREND("new", "建档趋势", ReportType.PATIENT),
    /** 住院概况是单行汇总指标，没有分组维度 */
    SUMMARY("summary", "全院汇总", ReportType.INPATIENT);

    private final String code;
    private final String name;
    private final Set<ReportType> supported;

    ReportDimension(String code, String name, ReportType... supported) {
        this.code = code;
        this.name = name;
        this.supported = Set.of(supported);
    }

    public static ReportDimension of(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "统计维度不能为空");
        }
        String trimmed = code.trim();
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID,
                        "统计维度非法：" + trimmed + "，可选值 " + Arrays.toString(codes())));
    }

    /**
     * 校验数据集与维度是否匹配，并返回归一化后的维度。
     *
     * <p>同时把「数据集自己的默认维度」补上：前端只选了数据集没选维度时，
     * 取该数据集第一个合法维度，而不是报错 —— 少一次往返。</p>
     */
    public static ReportDimension match(ReportType type, String dimensionCode) {
        if (dimensionCode == null || dimensionCode.isBlank()) {
            return Arrays.stream(values())
                    .filter(item -> item.supported.contains(type))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID,
                            "数据集 " + type.getCode() + " 暂无可用维度"));
        }
        ReportDimension dimension = of(dimensionCode);
        if (!dimension.supported.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "数据集 " + type.getCode() + " 不支持维度 " + dimension.code
                            + "，可选维度 " + Arrays.toString(dimensionsOf(type)));
        }
        return dimension;
    }

    public static String[] dimensionsOf(ReportType type) {
        return Arrays.stream(values())
                .filter(item -> item.supported.contains(type))
                .map(ReportDimension::getCode)
                .toArray(String[]::new);
    }

    public static String[] codes() {
        return Arrays.stream(values()).map(ReportDimension::getCode).toArray(String[]::new);
    }

    /** 展示形式白名单：图表类型不接受自由字符串 */
    public static String normalizeChartType(String chartType) {
        List<String> allowed = List.of("table", "bar", "line", "pie");
        if (chartType == null || chartType.isBlank()) {
            return "table";
        }
        String trimmed = chartType.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(trimmed)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "展示形式非法：" + chartType + "，可选值 " + allowed);
        }
        return trimmed;
    }
}
