package com.hospital.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典项入参。
 *
 * <p>{@code itemValue} 用 {@code @NotBlank} 而不是 {@code @NotNull}：
 * 空串能通过 {@code @NotNull}，存进库就是一个「显示为空的选项」，
 * 使用者点下去存进去的是空字符串，查的时候既查不到也看不出来。</p>
 */
@Getter
@Setter
public class DictItemRequest {

    @NotBlank(message = "字典项名称不能为空")
    @Size(max = 50, message = "字典项名称不能超过 50 个字符")
    private String itemLabel;

    @NotBlank(message = "字典项取值不能为空")
    @Size(max = 50, message = "字典项取值不能超过 50 个字符")
    private String itemValue;

    private Integer sort;

    @Size(max = 50, message = "样式类名不能超过 50 个字符")
    private String cssClass;

    /** 0 停用 / 1 启用 */
    private Integer status = 1;
}
