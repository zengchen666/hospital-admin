package com.hospital.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 医生排班 biz_schedule，一条记录 = 某医生某天某时段某号别的一批号源。
 *
 * <p><b>剩余号源绝不能「先查后改」</b>：{@code remain_num} 的扣减必须走
 * 条件更新 {@code ... SET remain_num = remain_num - 1 WHERE id = ? AND remain_num > 0}，
 * 影响行数为 0 即表示号源已抢完。先查再改在并发下必然超卖。</p>
 */
@Getter
@Setter
@TableName("biz_schedule")
public class BizSchedule extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long doctorId;

    private Long deptId;

    private LocalDate scheduleDate;

    /** 时段：1 上午 / 2 下午 / 3 晚上 */
    private Integer timePeriod;

    /** 号别：1 普通 / 2 专家 / 3 急诊 */
    private Integer regType;

    private Integer totalNum;

    /** 剩余号源，只能通过条件更新维护 */
    private Integer remainNum;

    private BigDecimal regFee;

    /** 0 停诊 / 1 正常 */
    private Integer status;
}
