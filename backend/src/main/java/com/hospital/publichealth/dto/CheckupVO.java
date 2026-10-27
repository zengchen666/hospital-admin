package com.hospital.publichealth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 体检记录视图对象。
 *
 * <p>{@code amount} 来自套餐快照价（散查为 0），出报告后不再变动。</p>
 */
@Getter
@Setter
@Builder
public class CheckupVO {

    private String id;
    private String checkupNo;
    private Long patientId;
    private String patientName;
    private Integer gender;
    private Integer age;
    private Long packageId;
    private String packageName;
    private BigDecimal amount;
    private LocalDate checkupDate;
    private String summary;
    private String abnormalItems;
    private Long reportFileId;

    /** 0 待检 / 1 已检待出报告 / 2 已出报告 */
    private Integer status;
}
