package com.hospital.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品统计结果（消耗 / 销售排行）。
 *
 * <p>口径是<b>已发药</b>（{@code biz_dispense}），不是已开方。
 * 处方开了但患者没缴费取药，药品并没有真正消耗掉 —— 用处方量做药品消耗
 * 会让库房备货一直偏多。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugStatVO {

    private String drugId;

    private String drugName;

    private String spec;

    /** 消耗数量 */
    private Long quantity;

    /** 销售金额 */
    private BigDecimal amount;

    /** 排名，从 1 开始 */
    private Integer rank;
}
