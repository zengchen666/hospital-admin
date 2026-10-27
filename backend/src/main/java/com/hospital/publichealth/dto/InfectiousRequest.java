package com.hospital.publichealth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 传染病报告卡登记入参。
 *
 * <p>{@code reportDate} 服务端会强制校验「不得晚于今天」——
 * 报告日期是法定时限的起算点，填未来日期等于伪造上报时间。</p>
 */
@Getter
@Setter
public class InfectiousRequest {

    @NotNull(message = "患者不能为空")
    private Long patientId;

    @NotBlank(message = "传染病名称不能为空")
    private String diseaseName;

    private String icdCode;

    @NotNull(message = "报告日期不能为空")
    private LocalDate reportDate;
}
