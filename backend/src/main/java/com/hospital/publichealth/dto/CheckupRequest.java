package com.hospital.publichealth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 体检登记入参。
 *
 * <p><b>刻意没有金额字段</b>：体检金额只认服务端套餐价。
 * 若开一个 {@code amount} 入参，改一下请求体就能 1 块钱做完全套体检 ——
 * 这与处方、检查收费「不接受前端报价」是同一条原则。</p>
 */
@Getter
@Setter
public class CheckupRequest {

    @NotNull(message = "患者不能为空")
    private Long patientId;

    /** 套餐 ID，散查（自选单项）时为空 */
    private Long packageId;

    @NotNull(message = "体检日期不能为空")
    private LocalDate checkupDate;
}
