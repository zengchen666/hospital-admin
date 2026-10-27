package com.hospital.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.pharmacy.entity.BizDispense;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizDispenseMapper extends BaseMapper<BizDispense> {

    /** 当天最大发药单号 */
    @Select("SELECT IFNULL(MAX(dispense_no), '') FROM biz_dispense WHERE dispense_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxDispenseNo(@Param("prefix") String prefix);

    /**
     * 发药状态流转（0 待发 → 1 已发），带原状态条件防重复发药。
     */
    @Update("""
            UPDATE biz_dispense
               SET status = #{toStatus},
                   operator_id = #{operatorId},
                   dispense_time = NOW(),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = #{fromStatus}
               AND deleted = 0
            """)
    int transitStatus(@Param("id") Long id,
                      @Param("fromStatus") Integer fromStatus,
                      @Param("toStatus") Integer toStatus,
                      @Param("operatorId") Long operatorId);

    /** 退药：1 已发 → 2 已退（带原状态条件，已退过的不能再退一次） */
    @Update("""
            UPDATE biz_dispense
               SET status = 2,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 1
               AND deleted = 0
            """)
    int returnBack(@Param("id") Long id, @Param("operatorId") Long operatorId);

    /** 按处方找已发药记录（退费时据此回退库存） */
    @Select("""
            SELECT * FROM biz_dispense
             WHERE presc_id = #{prescId}
               AND status = 1
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    BizDispense selectDoneByPresc(@Param("prescId") Long prescId);
}
