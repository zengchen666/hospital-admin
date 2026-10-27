package com.hospital.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查（检验 / 影像）申请单 biz_exam_apply。
 *
 * <p>状态机：<b>0 待缴费 → 1 待采样 → 2 检验中 → 3 已出报告</b>。
 * 本期未接收费模块，开单直接进入「待采样」，
 * 接收费后把开单状态改回 0、由收费成功回调推进到 1 即可。</p>
 */
@Getter
@Setter
@TableName("biz_exam_apply")
public class BizExamApply extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String applyNo;

    /** 样本条码号，首次打印条码时生成；重打保持同一号，避免同一管血两个码 */
    private String barcodeNo;

    private Long patientId;

    /** 就诊 ID（一期 = biz_registration.id），用于从就诊串起检查单 */
    private Long visitId;

    private Long doctorId;

    /** 1 检验 / 2 检查（影像） */
    private Integer examType;

    /** 项目名称摘要，明细见 biz_exam_item */
    private String examItem;

    private String bodyPart;

    private String clinicalDiagnosis;

    /** 0 待缴费 / 1 待采样 / 2 检验中 / 3 已出报告 */
    private Integer status;

    private LocalDateTime applyTime;
}
