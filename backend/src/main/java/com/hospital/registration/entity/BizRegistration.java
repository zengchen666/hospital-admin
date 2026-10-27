package com.hospital.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 挂号记录 biz_registration。
 *
 * <p>一期以本表 id 充当 {@code visit_id}，处方、病历等都挂在挂号记录上，
 * 这样「一次就诊」天然是一条主线，不用额外造就诊表。</p>
 *
 * <p>状态机：0 待就诊 → 1 就诊中 → 2 已完成；0/1 可退号 → 3 已退号。
 * 退号必须同时回补号源，见 {@code RegistrationService#cancel}。</p>
 */
@Getter
@Setter
@TableName("biz_registration")
public class BizRegistration extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 挂号单号，如 REG202610260001 */
    private String regNo;

    private Long patientId;

    private Long doctorId;

    private Long deptId;

    private Long scheduleId;

    /** 号别：1 普通 / 2 专家 / 3 急诊 */
    private Integer regType;

    private BigDecimal regFee;

    /** 排队号，按排班维度递增 */
    private Integer queueNo;

    /** 0 待就诊 / 1 就诊中 / 2 已完成 / 3 已退号 */
    private Integer status;

    private LocalDateTime regTime;
}
