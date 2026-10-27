package com.hospital.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.exam.entity.BizExamReport;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizExamReportMapper extends BaseMapper<BizExamReport> {

    @Select("SELECT IFNULL(MAX(report_no), '') FROM biz_exam_report WHERE report_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxReportNo(@Param("prefix") String prefix);

    /** 按申请单取报告（一张申请单一份报告） */
    @Select("""
            SELECT * FROM biz_exam_report
             WHERE apply_id = #{applyId} AND deleted = 0
             LIMIT 1
            """)
    BizExamReport selectByApplyId(@Param("applyId") Long applyId);

    /**
     * 报告发布（0 草稿 → 1 已发布），带原状态条件防重复发布。
     */
    @Update("""
            UPDATE biz_exam_report
               SET status = #{toStatus},
                   auditor_id = #{auditorId},
                   report_time = NOW(),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = #{fromStatus}
               AND deleted = 0
            """)
    int transitStatus(@Param("id") Long id,
                      @Param("fromStatus") Integer fromStatus,
                      @Param("toStatus") Integer toStatus,
                      @Param("auditorId") Long auditorId);

    /**
     * 危急值处理登记：未处理 → 已处理。
     *
     * <p>{@code handle_status = 0} 条件保证并发下不会重复登记；
     * 重复登记会让「谁处理的」被后一个人覆盖，闭环记录失去意义。</p>
     */
    @Update("""
            UPDATE biz_exam_report
               SET handle_status = 1,
                   handler_id = #{handlerId},
                   handle_time = NOW(),
                   handle_remark = #{remark},
                   update_time = NOW()
             WHERE id = #{id}
               AND is_critical = 1
               AND status = 1
               AND IFNULL(handle_status, 0) = 0
               AND deleted = 0
            """)
    int handleCritical(@Param("id") Long id,
                       @Param("handlerId") Long handlerId,
                       @Param("remark") String remark);
}
