package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizCharge;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface BizChargeMapper extends BaseMapper<BizCharge> {

    /** 当天最大收费单号，用于序列懒初始化 */
    @Select("SELECT IFNULL(MAX(charge_no), '') FROM biz_charge WHERE charge_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxChargeNo(@Param("prefix") String prefix);

    /**
     * 按支付方式汇总已收款流水（日结用）。
     *
     * <p>只统计 {@code pay_status IN (1,2,3)}：待支付（0）的单还没收到钱，
     * 算进当日收入会让收费员对着一个「钱还没进抽屉」的数点现金。</p>
     */
    @Select("""
            SELECT pay_type                     AS payType,
                   IFNULL(SUM(actual_amount),0)  AS amount,
                   COUNT(*)                      AS count
              FROM biz_charge
             WHERE charge_time >= #{from}
               AND charge_time <  #{to}
               AND pay_status IN (1, 2, 3)
               AND deleted = 0
             GROUP BY pay_type
            """)
    List<com.hospital.finance.service.DailySettleService.PayRow> sumByPayType(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 按日汇总（月结用） */
    @Select("""
            SELECT DATE_FORMAT(charge_time, '%Y-%m-%d') AS day,
                   IFNULL(SUM(actual_amount),0)         AS amount,
                   COUNT(*)                             AS count
              FROM biz_charge
             WHERE charge_time >= #{from}
               AND charge_time <  #{to}
               AND pay_status IN (1, 2, 3)
               AND deleted = 0
             GROUP BY DATE_FORMAT(charge_time, '%Y-%m-%d')
             ORDER BY day
            """)
    List<com.hospital.finance.service.DailySettleService.DayRow> sumByDay(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 支付状态流转（待支付 → 已支付）。
     *
     * <p>带原状态条件：并发下同一张收费单只能被成功支付一次，
     * 不会出现「收两遍钱」。</p>
     */
    @Update("""
            UPDATE biz_charge
               SET pay_status = #{toStatus},
                   operator_id = #{operatorId},
                   charge_time = NOW(),
                   update_time = NOW()
             WHERE id = #{id}
               AND pay_status = #{fromStatus}
               AND deleted = 0
            """)
    int transitPayStatus(@Param("id") Long id,
                         @Param("fromStatus") Integer fromStatus,
                         @Param("toStatus") Integer toStatus,
                         @Param("operatorId") Long operatorId);
}
