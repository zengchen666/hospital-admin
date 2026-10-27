package com.hospital.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 开单时的检验项目。
 */
@Getter
@Setter
public class ExamItemRequest {

    @NotBlank(message = "项目编码不能为空")
    @Size(max = 30, message = "项目编码长度不能超过 30")
    private String itemCode;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过 100")
    private String itemName;

    private Integer sort;
}
