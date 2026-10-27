package com.hospital.inpatient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 床位 biz_bed。
 *
 * <p><b>状态是并发争夺的焦点</b>：{@code 0 空闲 / 1 占用 / 2 维修}。
 * 占用必须走条件更新 {@code SET status = 1 WHERE id = ? AND status = 0}，
 * 影响行数为 0 就说明被别人抢走了 —— 先查后改必然出现「一床住两人」。</p>
 */
@Getter
@Setter
@TableName("biz_bed")
public class BizBed extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long wardId;

    private String bedNo;

    /** 1 普通 / 2 重症 / 3 VIP */
    private Integer bedType;

    /** 床位费 / 天 */
    private java.math.BigDecimal price;

    /** 0 空闲 / 1 占用 / 2 维修 */
    private Integer status;
}
