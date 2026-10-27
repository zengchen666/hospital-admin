package com.hospital.registration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 挂号请求。
 *
 * <p>只传排班 ID 与患者 ID：医生、科室、号别、挂号费全部由排班带出，
 * 前端传的价格一律忽略 —— 否则改一下请求体就能一元挂号。</p>
 */
@Getter
@Setter
public class RegistrationRequest {

    @NotNull(message = "患者不能为空")
    private Long patientId;

    @NotNull(message = "排班不能为空")
    private Long scheduleId;

    /** 可选：不传时取排班上的号别 */
    private Integer regType;
}
