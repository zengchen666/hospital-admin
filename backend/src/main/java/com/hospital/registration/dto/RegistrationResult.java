package com.hospital.registration.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 挂号成功后的回执，与接口文档 4.2 的响应结构一致。
 */
@Getter
@Builder
public class RegistrationResult {

    private final String id;

    private final String regNo;

    private final Integer queueNo;

    private final BigDecimal regFee;

    /** 0 待就诊 */
    private final Integer status;
}
