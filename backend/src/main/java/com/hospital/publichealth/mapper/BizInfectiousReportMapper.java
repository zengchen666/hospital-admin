package com.hospital.publichealth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.publichealth.entity.BizInfectiousReport;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizInfectiousReportMapper extends BaseMapper<BizInfectiousReport> {

    /**
     * 该患者该病种是否已有待上报的卡片（防重复上报）。
     *
     * <p>只拦「待上报」的：已上报过的病种若再次感染（如二次新冠），
     * 报新卡是合理的，不该被这条路挡住。</p>
     *
     * <p>匹配按病名而非 ICD —— ICD 允许为空，按它去重会漏判。</p>
     */
    @Select("""
            SELECT COUNT(1) FROM biz_infectious_report
             WHERE patient_id = #{patientId}
               AND disease_name = #{diseaseName}
               AND report_status = 0
               AND deleted = 0
            """)
    int countPending(@Param("patientId") Long patientId, @Param("diseaseName") String diseaseName);

    /**
     * 上报：0 待上报 → 1 已上报，带原状态条件防并发送重。
     *
     * @return 1 成功；0 表示已被上报过（并发场景）
     */
    @Update("""
            UPDATE biz_infectious_report
               SET report_status = 1,
                   reporter_id = COALESCE(#{reporterId}, reporter_id),
                   update_by = COALESCE(#{reporterId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND report_status = 0
               AND deleted = 0
            """)
    int report(@Param("id") Long id, @Param("reporterId") Long reporterId);

    /**
     * 登记疾控反馈。
     *
     * <p><b>只追加反馈，不改病名/日期</b>：已上报的卡片等同于报送出去的文书，
     * 悄悄改内容会让人对不上疾控那边的存档。</p>
     */
    @Update("""
            UPDATE biz_infectious_report
               SET cdc_response = #{response},
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND deleted = 0
            """)
    int feedback(@Param("id") Long id,
                 @Param("response") String response,
                 @Param("operatorId") Long operatorId);
}
