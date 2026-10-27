package com.hospital.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典入参。
 *
 * <p>{@code dictType} 限定 {@code 小写字母 + 下划线}：
 * 它是被业务代码直接引用的键（{@code dictService.items("pay_type")}），
 * 允许大写或连字符会出现「同一个字典两种写法」，
 * 而且大小写混用在某些文件系统上是能过、在某些上是过不了的。</p>
 */
@Getter
@Setter
public class DictRequest {

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 50, message = "字典类型不能超过 50 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字典类型只能由小写字母、数字和下划线组成，且以字母开头")
    private String dictType;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 50, message = "字典名称不能超过 50 个字符")
    private String dictName;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status = 1;
}
