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
 * 住院登记 biz_admission，一次住院一条记录。
 *
 * <p>状态：<b>0 在院 → 1 出院</b>（2 转科预留）。
 * 出院时必须同时做三件事：置状态、写出院时间、<b>释放床位</b> ——
 * 少做最后一步，那张床就永远占着，别人住不进来。</p>
 */
@Getter
@Setter
@TableName("biz_admission")
public class BizAdmission extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String admissionNo;

    private Long patientId;

    private Long deptId;

    /** 床位 ID，未分配时为空 */
    private Long bedId;

    private Long doctorId;

    private Long nurseId;

    private LocalDateTime admissionTime;

    private LocalDateTime dischargeTime;

    private String diagnosis;

    /** 押金（预交金） */
    private BigDecimal deposit;

    /** 0 在院 / 1 出院 / 2 转科 */
    private Integer status;
}
