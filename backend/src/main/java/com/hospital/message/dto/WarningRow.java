package com.hospital.message.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预警原始行（Mapper 返回值，不对外暴露）。
 *
 * <p><b>为什么不让 SQL 直接拼好 title/content</b>：预警内容里带患者姓名，
 * 而患者姓名在列表里必须脱敏（{@link com.hospital.common.util.MaskUtil}）。
 * 让 SQL 去拼字符串，脱敏规则就得在 SQL 里再实现一遍 ——
 * 脱敏口径改了，SQL 那边不会跟着改，迟早漏。所以行里只带字段，文案在 Java 侧统一拼。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WarningRow {

    /** 关联业务 ID，点击预警跳转用 */
    private Long bizId;

    /** 主体名称：药品名 / 疫苗名 / 病种名 / 检查项目 */
    private String subject;

    /** 患者姓名，可空（库存预警没有患者） */
    private String patientName;

    /** 补充说明：批号 / 报告号 / 剂次 */
    private String detail;

    /** 触发数值：当前库存、剩余天数、逾期天数 */
    private BigDecimal value;

    /** 阈值，用于分档（库存预警值）；其余场景为 null */
    private BigDecimal warnValue;

    /** 相关日期：效期 / 报告日期 / 应种日期 */
    private LocalDate dueDate;

    /** 距离 dueDate 的天数，正数=已逾期，负数=还有几天 */
    private Integer days;
}
