package com.hospital.inpatient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 护理记录 biz_nursing_record。
 *
 * <p>一张表承载三类记录（1 护理记录 / 2 体温单 / 3 生命体征），
 * 是为了避免三张同构表各写一套 CRUD。<b>代价是查询必须带 record_type 过滤</b> ——
 * 忘了过滤，体温单页面会混进护理记录，而且看不出错在哪。见 NursingService。</p>
 *
 * <p>校验规则：type=1 要求 content 必填；type=2/3 要求体温脉搏等体征字段有值。</p>
 */
@Getter
@Setter
@TableName("biz_nursing_record")
public class BizNursingRecord extends BaseEntity {

    /** 1 护理记录 / 2 体温单 / 3 生命体征 */
    public static final int TYPE_NURSING = 1;
    public static final int TYPE_TEMPERATURE = 2;
    public static final int TYPE_VITAL = 3;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long admissionId;

    private Integer recordType;

    private LocalDateTime recordTime;

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 脉搏（次/分） */
    private Integer pulse;

    /** 呼吸（次/分） */
    private Integer breath;

    /** 血压，如 120/80 */
    private String bloodPressure;

    /** 血氧饱和度（%） */
    private Integer spo2;

    /** 入量（ml） */
    private Integer intake;

    /** 出量（ml） */
    private Integer output;

    private String content;

    private Long nurseId;
}
