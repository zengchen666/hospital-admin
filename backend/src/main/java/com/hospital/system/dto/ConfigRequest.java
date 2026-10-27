package com.hospital.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数入参。
 *
 * <p>{@code configKey} 与字典类型同样的约束：小写字母 + 点分或下划线，
 * 因为它是程序里按名字取值的键（{@code configService.get("login.max_attempts")}）。
 * 放开命名等于允许「同一个参数两种写法」。</p>
 */
@Getter
@Setter
public class ConfigRequest {

    @NotBlank(message = "参数键不能为空")
    @Size(max = 100, message = "参数键不能超过 100 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_.]*$", message = "参数键只能由小写字母、数字、下划线和点组成，且以字母开头")
    private String configKey;

    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称不能超过 100 个字符")
    private String configName;

    @Size(max = 500, message = "参数值不能超过 500 个字符")
    private String configValue;

    /** 1 系统内置 / 2 业务参数 */
    private Integer configType = 2;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
