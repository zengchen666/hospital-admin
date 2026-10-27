package com.hospital.registration.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 排班 / 号源查询条件。
 *
 * <p>日期区间复用父类的 {@code startTime} / {@code endTime}（yyyy-MM-dd）。</p>
 */
@Getter
@Setter
public class ScheduleQuery extends PageQuery {

    private Long doctorId;

    private Long deptId;

    /** 1 上午 / 2 下午 / 3 晚上 */
    private Integer timePeriod;

    /** 1 普通 / 2 专家 / 3 急诊 */
    private Integer regType;

    /** 0 停诊 / 1 正常；不传表示全部 */
    private Integer status;

    /** true 时只返回仍有剩余号源的排班（挂号页用） */
    private Boolean onlyAvailable;
}
