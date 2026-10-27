package com.hospital.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.pharmacy.entity.BizPurchase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizPurchaseMapper extends BaseMapper<BizPurchase> {

    @Select("SELECT IFNULL(MAX(purchase_no), '') FROM biz_purchase WHERE purchase_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxPurchaseNo(@Param("prefix") String prefix);

    /**
     * 提交审核：草稿 → 待审核。
     *
     * @return 0 表示状态已被别人改过
     */
    @Update("""
            UPDATE biz_purchase
               SET status = 1,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int submit(@Param("id") Long id, @Param("operatorId") Long operatorId);

    /**
     * 审核通过：待审核 → 已审核。
     *
     * <p><b>这里不加库存</b>：审核代表同意采购，入库是货到验收后的独立动作。</p>
     */
    @Update("""
            UPDATE biz_purchase
               SET status = 2,
                   auditor_id = #{auditorId},
                   audit_time = NOW(),
                   update_by = COALESCE(#{auditorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 1
               AND deleted = 0
            """)
    int audit(@Param("id") Long id, @Param("auditorId") Long auditorId);

    /** 入库完成：已审核 → 已入库 */
    @Update("""
            UPDATE biz_purchase
               SET status = 3,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 2
               AND deleted = 0
            """)
    int markStocked(@Param("id") Long id, @Param("operatorId") Long operatorId);

    /** 作废：草稿 / 待审核 / 已审核 都可作废，已入库的单子不能作废 */
    @Update("""
            UPDATE biz_purchase
               SET status = 4,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status IN (0, 1, 2)
               AND deleted = 0
            """)
    int voidPurchase(@Param("id") Long id, @Param("operatorId") Long operatorId);
}
