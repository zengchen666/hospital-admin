package com.hospital.message.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预警项。
 *
 * <p>聚合自多个业务的实时数据，<b>不落库</b> ——
 * 落库就成了快照，库存补上了预警还在，反而没人再信这个列表。</p>
 */
@Getter
@Setter
@Builder
public class WarningVO {

    /** stock_warn / drug_expire / critical / infectious_overdue / vaccine_overdue */
    private String bizType;

    private String bizTypeName;

    /** 关联业务 ID，点击跳转用 */
    private String bizId;

    private String title;

    private String content;

    /** 1 提示 / 2 警告 / 3 紧急 */
    private Integer level;
    private String levelName;

    /** 触发预警的具体数值，如当前库存、剩余天数 */
    private BigDecimal value;

    private LocalDate dueDate;
}
