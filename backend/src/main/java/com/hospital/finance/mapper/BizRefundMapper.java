package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizRefund;
import com.hospital.finance.service.DailySettleService;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface BizRefundMapper extends BaseMapper<BizRefund> {

    @Select("SELECT IFNULL(MAX(refund_no), '') FROM biz_refund WHERE refund_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxRefundNo(@Param("prefix") String prefix);

    /**
     * 当日退费汇总（日结用）。
     *
     * <p>只统计 {@code status = 2 已退款}：申请了没审、审了没退，
     * 钱都还在医院账上，算进当日支出会让收费员以为抽屉少了钱。</p>
     *
     * <p>{@code cashAmount} 单独算「原路退回是现金」的部分 ——
     * 只有它是真的从抽屉里拿出去的，其余走原渠道退回，不影响现金。</p>
     */
    @Select("""
            SELECT IFNULL(SUM(refund_amount),0)                                   AS amount,
                   IFNULL(SUM(CASE WHEN original_pay = 1 THEN refund_amount END),0) AS cashAmount,
                   COUNT(*)                                                        AS count
              FROM biz_refund
             WHERE refund_time >= #{from}
               AND refund_time <  #{to}
               AND status = 2
               AND deleted = 0
            """)
    DailySettleService.RefundRow sumRefund(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /** 按日退费汇总（月结用） */
    @Select("""
            SELECT DATE_FORMAT(refund_time, '%Y-%m-%d') AS day,
                   IFNULL(SUM(refund_amount),0)         AS amount,
                   COUNT(*)                             AS count
              FROM biz_refund
             WHERE refund_time >= #{from}
               AND refund_time <  #{to}
               AND status = 2
               AND deleted = 0
             GROUP BY DATE_FORMAT(refund_time, '%Y-%m-%d')
             ORDER BY day
            """)
    List<DailySettleService.DayRow> sumRefundByDay(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    /**
     * 退费状态流转（带原状态条件）。
     *
     * <p>并发下两个窗口同时点「审核」，只有一个能把 0 改成 1，
     * 另一个影响行数为 0 —— 不会出现「审两遍、退两遍」。</p>
     */
    @Update("""
            UPDATE biz_refund
               SET status = #{toStatus},
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = #{fromStatus}
               AND deleted = 0
            """)
    int transitStatus(@Param("id") Long id,
                      @Param("fromStatus") Integer fromStatus,
                      @Param("toStatus") Integer toStatus,
                      @Param("operatorId") Long operatorId);

    /** 审核：记审核人与审核时间，状态 0 → 1 */
    @Update("""
            UPDATE biz_refund
               SET status = 1,
                   auditor_id = #{auditorId},
                   audit_time = NOW(),
                   update_by = #{auditorId},
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int approve(@Param("id") Long id, @Param("auditorId") Long auditorId);

    /** 执行退款：状态 1 → 2，写退款时间 */
    @Update("""
            UPDATE biz_refund
               SET status = 2,
                   refund_time = NOW(),
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 1
               AND deleted = 0
            """)
    int complete(@Param("id") Long id, @Param("operatorId") Long operatorId);
}
