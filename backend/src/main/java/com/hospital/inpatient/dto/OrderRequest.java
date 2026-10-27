package com.hospital.inpatient.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 开立医嘱请求。
 */
@Getter
@Setter
public class OrderRequest {

    @NotNull(message = "住院记录不能为空")
    private Long admissionId;

    /** 1 长期 / 2 临时 */
    @NotNull(message = "医嘱类型不能为空")
    @Min(value = 1, message = "医嘱类型取值非法")
    @Max(value = 2, message = "医嘱类型取值非法")
    private Integer orderType;

    @NotBlank(message = "医嘱内容不能为空")
    @Size(max = 500, message = "医嘱内容长度不能超过 500")
    private String content;

    private Long drugId;

    @Size(max = 50, message = "用量长度不能超过 50")
    private String dosage;

    @Size(max = 20, message = "频次长度不能超过 20")
    private String frequency;

    /** 开始时间，不传则取当前时间 */
    private LocalDateTime startTime;
}
