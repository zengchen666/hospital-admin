package com.hospital.inpatient.dto;

import com.hospital.common.api.PageQuery;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;

/**
 * 护理记录分页查询。
 *
 * <p>{@code recordType} 是<b>必填</b>的语义过滤项：一张表三类记录，
 * 不带它查出来的列表是三类混在一起的，体温单上会出现「患者今日翻身两次」这种东西。</p>
 */
@Getter
@Setter
public class NursingQuery extends PageQuery {

    private Long admissionId;

    /** 1 护理记录 / 2 体温单 / 3 生命体征 */
    @Min(value = 1, message = "记录类型取值非法")
    @Max(value = 3, message = "记录类型取值非法")
    private Integer recordType;
}
