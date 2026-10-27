package com.hospital.inpatient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院医嘱 biz_medical_order。
 *
 * <p>类型：1 长期（持续执行直到停止）/ 2 临时（执行一次即结束）。
 * 状态：0 未执行 → 1 已执行 → 2 已停止。</p>
 *
 * <p>长期医嘱停止后不可重新启用，需要重新开立 —— 医嘱是法律文书，
 * 不能像开关一样来回切。</p>
 */
@Getter
@Setter
@TableName("biz_medical_order")
public class BizMedicalOrder extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long admissionId;

    /** 1 长期 / 2 临时 */
    private Integer orderType;

    private String content;

    private Long drugId;

    private String dosage;

    private String frequency;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;

    private Long doctorId;

    /** 0 未执行 / 1 已执行 / 2 已停止 */
    private Integer status;
}
