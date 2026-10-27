package com.hospital.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.report.entity.SysReportConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysReportConfigMapper extends BaseMapper<SysReportConfig> {

    /**
     * 取<b>系统内置</b>的报表配置（快照挂靠用）。
     *
     * <p>{@code creator_id IS NULL} 是内置的判据：用户自建的报表
     * {@code creator_id} 为当前用户。少了这个条件，用户建一份同为
     * {@code income} 的报表后，快照就可能挂到那份上 ——
     * 快照是「全院口径的历史存档」，不该跟着某人的个人配置走。</p>
     */
    @Select("""
            SELECT * FROM sys_report_config
             WHERE report_type = #{reportType}
               AND creator_id IS NULL
               AND deleted = 0
             LIMIT 1
            """)
    SysReportConfig selectByType(@Param("reportType") String reportType);
}
