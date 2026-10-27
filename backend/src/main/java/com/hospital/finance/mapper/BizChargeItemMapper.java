package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizChargeItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 收费明细 Mapper。查询统一 {@code in(chargeId)} 批量取。
 */
public interface BizChargeItemMapper extends BaseMapper<BizChargeItem> {

    /**
     * 回冲已退数量：<b>防超退的唯一保证</b>。
     *
     * <p>条件里带 {@code quantity - refund_num >= num}，由行锁保证原子性。
     * 先查「还能退多少」再 update 必然穿 —— 两个窗口同时退同一项，
     * 两边都读到「还能退 2」，于是各退 2，实际只卖了 2 件却退了 4 件。</p>
     *
     * @return 1 成功；0 表示超出可退数量
     */
    @Update("""
            UPDATE biz_charge_item
               SET refund_num = refund_num + #{num},
                   update_time = NOW()
             WHERE id = #{id}
               AND quantity - IFNULL(refund_num, 0) >= #{num}
               AND deleted = 0
            """)
    int addRefundNum(@Param("id") Long id, @Param("num") int num);
}
