package com.hospital.inpatient.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 转科请求。
 *
 * <p>目标科室与接收医生至少填一项：转科有时是「换科室等分配医生」，
 * 有时是「同科室换主管医生」，强制两项都填会让第二种做不了。</p>
 */
@Getter
@Setter
public class TransferRequest {

    /** 目标科室 */
    private Long deptId;

    /** 接收医生 */
    private Long doctorId;
}
