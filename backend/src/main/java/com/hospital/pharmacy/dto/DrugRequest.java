package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品新增 / 修改。
 *
 * <p><b>售价必须 ≥ 进价</b>：这条在 Service 里校验而不是这里 ——
 * 医院确实存在「进价高于售价」的政策性亏损药品（如部分急救药），
 * 直接一刀切拒绝会把合规业务挡在门外，所以后端只做提示性校验：
 * 售价为 0 或负数一定非法，倒挂则写告警日志放行。</p>
 */
@Getter
@Setter
public class DrugRequest {

    @NotBlank(message = "药品编码不能为空")
    @Size(max = 30, message = "药品编码长度不能超过 30")
    private String drugCode;

    @NotBlank(message = "药品名称不能为空")
    @Size(max = 100, message = "药品名称长度不能超过 100")
    private String drugName;

    @Size(max = 100, message = "通用名长度不能超过 100")
    private String genericName;

    @Size(max = 50, message = "规格长度不能超过 50")
    private String spec;

    /** 剂型，如 片剂 / 注射剂 */
    @Size(max = 30, message = "剂型长度不能超过 30")
    private String dosageForm;

    @NotBlank(message = "单位不能为空")
    @Size(max = 20, message = "单位长度不能超过 20")
    private String unit;

    @Size(max = 100, message = "生产厂家长度不能超过 100")
    private String manufacturer;

    @Size(max = 50, message = "批准文号长度不能超过 50")
    private String approvalNo;

    @NotNull(message = "进价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "进价必须大于 0")
    private BigDecimal purchasePrice;

    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "售价必须大于 0")
    private BigDecimal salePrice;

    @Size(max = 50, message = "分类长度不能超过 50")
    private String category;

    /** 0 否 / 1 麻精毒麻 */
    @Min(value = 0, message = "是否特殊药品取值非法")
    private Integer isSpecial;

    /** 0 停用 / 1 启用 */
    @Min(value = 0, message = "状态取值非法")
    private Integer status;
}
