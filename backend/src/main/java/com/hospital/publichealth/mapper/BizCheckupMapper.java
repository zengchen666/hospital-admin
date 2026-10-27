package com.hospital.publichealth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.publichealth.entity.BizCheckup;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizCheckupMapper extends BaseMapper<BizCheckup> {

    @Select("SELECT IFNULL(MAX(checkup_no), '') FROM biz_checkup WHERE checkup_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCheckupNo(@Param("prefix") String prefix);

    /**
     * 体检状态流转，带原状态条件。
     *
     * @return 1 成功；0 表示状态已被并发改过
     */
    @Update("""
            UPDATE biz_checkup
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

    /**
     * 出具体检报告：1 已检待出报告 → 2 已出报告，同时写入结论与异常项。
     *
     * <p>条件里带上 {@code status = 1} 而不是只判 id：
     * 待检的单子跳过检查直接出报告，等于伪造体检结果。</p>
     */
    @Update("""
            UPDATE biz_checkup
               SET status = 2,
                   summary = #{summary},
                   abnormal_items = #{abnormalItems},
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 1
               AND deleted = 0
            """)
    int publishReport(@Param("id") Long id,
                      @Param("summary") String summary,
                      @Param("abnormalItems") String abnormalItems,
                      @Param("operatorId") Long operatorId);
}
