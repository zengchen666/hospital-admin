package com.hospital.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊病历 biz_medical_record（EMR）。
 *
 * <p><b>一次挂号一份病历</b>：{@code visit_id} 上有唯一索引
 * （{@code uk_record_visit}），一期它就是 {@code biz_registration.id}。
 * 没有这个字段时病历只能按患者查，病历与就诊就串不起来 —— 复诊时翻不到上次记录。</p>
 *
 * <p>状态：0 草稿（可反复保存）→ 1 已提交（进入待归档）→ 2 已归档。
 * 提交后不允许再改，需要修正走「作废重开」，保证病历的原始性。</p>
 */
@Getter
@Setter
@TableName("biz_medical_record")
public class BizMedicalRecord extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    /** 就诊 ID（一期 = biz_registration.id），唯一 */
    private Long visitId;

    /** 就诊流水号，一期直接复用挂号单号 */
    private String visitNo;

    /** 1 门诊 / 2 住院 */
    private Integer visitType;

    /** 主诉 */
    private String chiefComplaint;

    /** 现病史 */
    private String presentIllness;

    /** 体格检查 */
    private String physicalExam;

    /** 诊断 */
    private String diagnosis;

    /** ICD-10 编码 */
    private String icdCode;

    private Long doctorId;

    private LocalDateTime visitTime;

    /** 0 草稿 / 1 已提交 / 2 已归档 */
    private Integer status;
}
