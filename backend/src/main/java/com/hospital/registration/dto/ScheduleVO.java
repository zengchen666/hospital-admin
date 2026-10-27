package com.hospital.registration.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 排班 / 号源视图对象。
 */
@Getter
@Builder
public class ScheduleVO {

    private final String id;

    private final Long doctorId;

    /** 服务端批量补齐，避免前端为每行再查一次医生 */
    private final String doctorName;

    private final Long deptId;

    private final LocalDate scheduleDate;

    /** 1 上午 / 2 下午 / 3 晚上 */
    private final Integer timePeriod;

    /** 1 普通 / 2 专家 / 3 急诊 */
    private final Integer regType;

    private final Integer totalNum;

    private final Integer remainNum;

    private final BigDecimal regFee;

    /** 0 停诊 / 1 正常 */
    private final Integer status;
}
