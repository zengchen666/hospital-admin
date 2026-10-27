package com.hospital.publichealth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 居民健康档案 biz_health_record。
 *
 * <p><b>一人一份</b>：表上有 {@code uk_health_patient(patient_id)} 唯一键，
 * 建档前必须先查一次，否则直接 40904。居民的慢病随访、体检结论都挂在这份档案上，
 * 出现两份会导致随访记录归属不清。</p>
 */
@Getter
@Setter
@TableName("biz_health_record")
public class BizHealthRecord extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    /** 档案号，HR + 日期 + 4 位序列 */
    private String recordNo;

    /** 慢病，逗号分隔，仅展示用（真正的随访数据应走 biz_follow_up） */
    private String chronicDisease;

    /** 吸烟：0 否 / 1 是 */
    private Integer smoking;

    /** 饮酒：0 否 / 1 是 */
    private Integer drinking;

    /** 运动频率，如「每周 3 次」 */
    private String exercise;
}
