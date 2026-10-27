package com.hospital.publichealth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 健康档案视图对象。
 *
 * <p>年龄由出生日期实时计算，不读缓存字段 —— 缓存的年龄会过期，
 * 在公卫统计里算错年龄分档会直接导致年报数据偏差。</p>
 */
@Getter
@Setter
@Builder
public class HealthRecordVO {

    private String id;
    private Long patientId;
    private String patientName;
    private Integer gender;
    private Integer age;
    private String recordNo;

    private String chronicDisease;
    private Integer smoking;
    private Integer drinking;
    private String exercise;

    private LocalDateTime createTime;
}
