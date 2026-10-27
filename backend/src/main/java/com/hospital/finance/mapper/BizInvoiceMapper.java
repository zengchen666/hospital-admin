package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizInvoice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizInvoiceMapper extends BaseMapper<BizInvoice> {

    /** 当天最大发票号，用于序列懒初始化 */
    @Select("SELECT IFNULL(MAX(invoice_no), '') FROM biz_invoice WHERE invoice_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxInvoiceNo(@Param("prefix") String prefix);

    @Select("""
            SELECT * FROM biz_invoice
             WHERE charge_id = #{chargeId}
               AND status = 0
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    BizInvoice selectValidByCharge(@Param("chargeId") Long chargeId);

    /**
     * 作废：0 正常 → 1 已作废。
     *
     * <p>带原状态条件，重复作废返回 0 行 —— 已经作废的票再点一次，
     * 不能让第二次「成功」，否则审计日志里会出现两条作废记录。</p>
     */
    @Update("""
            UPDATE biz_invoice
               SET status = 1,
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int voidInvoice(@Param("id") Long id);
}
