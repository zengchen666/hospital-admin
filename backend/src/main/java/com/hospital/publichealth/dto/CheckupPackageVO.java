package com.hospital.publichealth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 体检套餐视图对象。
 *
 * <p>{@code itemIds} 原样返回文本（前端表单要回填），
 * {@code itemNames} 是解析后查出来的项目名称，列表页展示用。</p>
 */
@Getter
@Setter
@Builder
public class CheckupPackageVO {

    private String id;
    private String packageName;
    private String itemIds;
    private List<String> itemNames;
    private BigDecimal price;
    private Integer status;
}
