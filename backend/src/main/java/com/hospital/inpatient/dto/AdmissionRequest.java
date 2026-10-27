package com.hospital.inpatient.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 入院登记请求。
 *
 * <p>{@code bedId} 可空：先登记后分床是常态（急诊入院时床位还没腾出来）。
 * 分床走独立接口，因为那一步是并发争抢的焦点，需要单独的错误语义。</p>
 */
@Getter
@Setter
public class AdmissionRequest {

    @NotNull(message = "患者不能为空")
    private Long patientId;

    @NotNull(message = "科室不能为空")
    private Long deptId;

    /** 床位 ID，可空（先登记后分床） */
    private Long bedId;

    @NotNull(message = "主治医生不能为空")
    private Long doctorId;

    private Long nurseId;

    @Size(max = 500, message = "入院诊断长度不能超过 500")
    private String diagnosis;

    @DecimalMin(value = "0.00", message = "预交金不能为负")
    private BigDecimal deposit;
}
