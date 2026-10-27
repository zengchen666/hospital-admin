package com.hospital.registration.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增排班请求。
 *
 * <p>{@code doctor_id + schedule_date + time_period + reg_type} 上有唯一索引，
 * 同一医生同一时段同一号别只能排一次，重复提交会返回 40904。</p>
 */
@Getter
@Setter
public class ScheduleSaveRequest {

    @NotNull(message = "医生不能为空")
    private Long doctorId;

    @NotNull(message = "科室不能为空")
    private Long deptId;

    @NotNull(message = "排班日期不能为空")
    private LocalDate scheduleDate;

    @NotNull(message = "时段不能为空")
    @Min(value = 1, message = "时段取值非法")
    @Max(value = 3, message = "时段取值非法")
    private Integer timePeriod;

    @NotNull(message = "号别不能为空")
    @Min(value = 1, message = "号别取值非法")
    @Max(value = 3, message = "号别取值非法")
    private Integer regType;

    @NotNull(message = "总号源不能为空")
    @Min(value = 0, message = "总号源不能为负")
    @Max(value = 500, message = "单时段号源不能超过 500")
    private Integer totalNum;

    @NotNull(message = "挂号费不能为空")
    @DecimalMin(value = "0.00", message = "挂号费不能为负")
    private BigDecimal regFee;
}
