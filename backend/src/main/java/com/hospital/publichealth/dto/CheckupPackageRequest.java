package com.hospital.publichealth.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 体检套餐维护入参。
 *
 * <p>{@code itemIds} 只接受「数字与逗号」，服务端会再校验一次字符白名单 ——
 * 这串文本后续会被拆成 ID 列表去查项目，直接放行等于开一个注入口子。</p>
 */
@Getter
@Setter
public class CheckupPackageRequest {

    @NotBlank(message = "套餐名称不能为空")
    private String packageName;

    /** 诊疗项目 ID 列表，逗号分隔，如 "9010,9011" */
    private String itemIds;

    @NotNull(message = "套餐价不能为空")
    @DecimalMin(value = "0", message = "套餐价不能为负")
    private BigDecimal price;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
