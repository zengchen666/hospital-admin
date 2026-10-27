package com.hospital.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.report.entity.SysReportData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface SysReportDataMapper extends BaseMapper<SysReportData> {

    /**
     * 写入某天的预聚合快照。
     *
     * <p>{@code ON DUPLICATE KEY UPDATE} 依赖 {@code uk_report_date} 唯一键：
     * <b>重复生成同一天是覆盖，不是报错，也不是插入第二条</b>。
     * 先查再插删会导致「同一天两份快照」，报表读到哪份全看运气。</p>
     */
    @Insert("""
            INSERT INTO sys_report_data (id, report_id, stat_date, data_json, create_time)
            VALUES (#{id}, #{reportId}, #{statDate}, #{dataJson}, NOW())
            ON DUPLICATE KEY UPDATE
                data_json   = VALUES(data_json),
                create_time = NOW()
            """)
    int upsert(SysReportData entity);

    @Select("""
            SELECT * FROM sys_report_data
             WHERE report_id = #{reportId}
               AND stat_date = #{statDate}
             LIMIT 1
            """)
    SysReportData selectByDate(@Param("reportId") Long reportId,
                               @Param("statDate") LocalDate statDate);
}
