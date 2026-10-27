package com.hospital.report.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;

/**
 * 报表类型白名单。
 *
 * <p><b>白名单不是为了优雅，是为了堵住注入</b>：报表接口最容易长成
 * 「前端传一段 SQL 或一个表名，后端拼一下就查」。那种实现一旦上线，
 * 拿到报表权限就等于拿到整个库的读权限，还顺带绕过所有数据权限。
 * 这里只接受 5 个固定值，其余一律 40001。</p>
 */
@Getter
public enum ReportType {

    OUTPATIENT("outpatient", "门诊统计"),
    INPATIENT("inpatient", "住院统计"),
    INCOME("income", "收入统计"),
    DRUG("drug", "药品统计"),
    PATIENT("patient", "患者分析");

    private final String code;
    private final String name;

    ReportType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ReportType of(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "报表类型不能为空");
        }
        String trimmed = code.trim();
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID,
                        "报表类型非法：" + trimmed + "，可选值 " + Arrays.toString(codes())));
    }

    public static String[] codes() {
        return Arrays.stream(values()).map(ReportType::getCode).toArray(String[]::new);
    }
}
