package com.hospital.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.exam.entity.BizExamApply;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizExamApplyMapper extends BaseMapper<BizExamApply> {

    /** 当天最大申请单号 */
    @Select("SELECT IFNULL(MAX(apply_no), '') FROM biz_exam_apply WHERE apply_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxApplyNo(@Param("prefix") String prefix);

    /**
     * 状态流转（带原状态条件，防并发下「已出报告的单被改回检验中」）。
     */
    @Update("""
            UPDATE biz_exam_apply
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
}
