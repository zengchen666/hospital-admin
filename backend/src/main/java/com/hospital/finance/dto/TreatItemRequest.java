package com.hospital.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 诊疗项目新增 / 调价请求。
 *
 * <p><b>价格必须带生效日期</b>：{@code effectiveDate} 为 null 时默认今天。
 * 调价不是「把旧价格覆盖掉」，而是新增一版 —— 上个月做的检查按上个月的价收，
 * 追溯计费时才知道当时该收多少。</p>
 */
@Getter
@Setter
public class TreatItemRequest {

    @NotBlank(message = "项目编码不能为空")
    @Size(max = 30, message = "项目编码长度不能超过 30")
    private String itemCode;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过 100")
    private String itemName;

    /** 1 诊疗 / 2 检查 / 3 检验 / 4 床位 / 5 手术 / 6 材料 */
    @NotNull(message = "项目类型不能为空")
    @Min(value = 1, message = "项目类型取值非法")
    @Max(value = 6, message = "项目类型取值非法")
    private Integer itemType;

    @Size(max = 50, message = "规格长度不能超过 50")
    private String spec;

    @Size(max = 20, message = "单位长度不能超过 20")
    private String unit;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能为负")
    private BigDecimal price;

    /** 1 甲类 / 2 乙类 / 3 丙类（自费） */
    @Min(value = 1, message = "医保类别取值非法")
    @Max(value = 3, message = "医保类别取值非法")
    private Integer insuranceType;

    /** 报销比例，0~100 */
    @DecimalMin(value = "0.00", message = "报销比例不能为负")
    private BigDecimal insuranceRatio;

    private Long deptId;

    /** 0 停用 / 1 启用 */
    private Integer status;

    /** 生效日期，null 视为今天 */
    private LocalDate effectiveDate;
}
