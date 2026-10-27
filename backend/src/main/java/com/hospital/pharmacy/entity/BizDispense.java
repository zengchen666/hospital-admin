package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发药记录 biz_dispense。
 */
@Getter
@Setter
@TableName("biz_dispense")
public class BizDispense extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String dispenseNo;

    /** 处方 ID（住院发药可为空） */
    private Long prescId;

    private Long patientId;

    private Long warehouseId;

    /** 1 门诊发药 / 2 住院发药 / 3 退药 */
    private Integer dispenseType;

    private BigDecimal totalAmount;

    private Long operatorId;

    private LocalDateTime dispenseTime;

    /** 0 待发 / 1 已发 / 2 已退 */
    private Integer status;
}
