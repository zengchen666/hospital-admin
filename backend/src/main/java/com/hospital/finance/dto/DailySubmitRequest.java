package com.hospital.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 提交日结。
 *
 * <p><b>差异非 0 时必须填差异说明</b>：短款 200 块钱写一句「抽屉钱不够」就提交，
 * 跟没对账没区别。说明进了审计日志，第二天查的是它，不是那 200 块。</p>
 */
@Getter
@Setter
public class DailySubmitRequest {

    @NotNull(message = "结账日期不能为空")
    private LocalDate settleDate;

    @NotNull(message = "实点现金不能为空")
    @DecimalMin(value = "0.00", message = "实点现金不能为负")
    private BigDecimal actualCash;

    @Size(max = 255, message = "差异说明长度不能超过 255")
    private String diffRemark;
}
