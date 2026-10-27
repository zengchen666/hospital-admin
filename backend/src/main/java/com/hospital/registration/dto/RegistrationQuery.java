package com.hospital.registration.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 挂号记录查询条件。日期区间复用父类的 startTime / endTime。
 */
@Getter
@Setter
public class RegistrationQuery extends PageQuery {

    private Long patientId;

    private Long doctorId;

    private Long deptId;

    private Long scheduleId;

    /** 0 待就诊 / 1 就诊中 / 2 已完成 / 3 已退号 */
    private Integer status;
}
