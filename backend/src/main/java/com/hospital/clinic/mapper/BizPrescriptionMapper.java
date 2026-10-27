package com.hospital.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.clinic.entity.BizPrescription;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizPrescriptionMapper extends BaseMapper<BizPrescription> {

    /**
     * 状态机流转：只有当前状态等于 {@code fromStatus} 才更新。
     *
     * <p>这是处方流转的唯一安全写法。若先查再改，两个药师同时审核同一张方、
     * 或审核与作废并发，就会互相覆盖，最后出现「已发药的处方被标成已退」这种
     * 无法对账的脏数据。</p>
     *
     * @return 1 表示流转成功；0 表示状态已变（并发或被别人处理过）
     */
    @Update("""
            UPDATE biz_prescription
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

    /** 当天最大处方号，用于序列懒初始化 */
    @Select("SELECT IFNULL(MAX(presc_no), '') FROM biz_prescription WHERE presc_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxPrescNo(@Param("prefix") String prefix);
}
