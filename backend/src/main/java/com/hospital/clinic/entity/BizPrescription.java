package com.hospital.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处方主表 biz_prescription。
 *
 * <p>状态机：<b>0 待审核 → 1 已审核 → 2 已收费 → 3 已发药</b>；
 * 0/1 可作废 → 4 已退。每次流转都必须用「带原状态的条件更新」，
 * 否则并发下会出现「已发药的处方被改成已退」这种无法对账的脏数据。</p>
 *
 * <p>{@code total_amount} 由服务端按明细汇总，绝不接收前端传来的总额。</p>
 */
@Getter
@Setter
@TableName("biz_prescription")
public class BizPrescription extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String prescNo;

    private Long patientId;

    private Long doctorId;

    /** 就诊 ID（一期 = biz_registration.id） */
    private Long visitId;

    /** 1 西药 / 2 中药 / 3 中成药 */
    private Integer prescType;

    private String diagnosis;

    private BigDecimal totalAmount;

    /** 0 待审核 / 1 已审核 / 2 已收费 / 3 已发药 / 4 已退 */
    private Integer status;
}
