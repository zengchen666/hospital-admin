package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商新增 / 修改。
 *
 * <p>联系电话用宽松正则而不是严格号段：供应商既有手机号也有带分机的座机，
 * 卡太死只会逼着录入员填假号码，反而不如不校验。</p>
 */
@Getter
@Setter
public class SupplierRequest {

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称长度不能超过 100")
    private String supplierName;

    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 30, message = "供应商编码长度不能超过 30")
    private String supplierCode;

    @Size(max = 50, message = "联系人长度不能超过 50")
    private String contactName;

    @Pattern(regexp = "^[0-9+\\-()\\s]{6,20}$", message = "联系电话格式不正确")
    private String contactPhone;

    @Size(max = 255, message = "地址长度不能超过 255")
    private String address;

    @Size(max = 100, message = "经营许可证号长度不能超过 100")
    private String licenseNo;

    /** 信用等级 1-5，5 最优 */
    @Min(value = 1, message = "信用等级取值 1-5")
    @Max(value = 5, message = "信用等级取值 1-5")
    private Integer creditLevel;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
