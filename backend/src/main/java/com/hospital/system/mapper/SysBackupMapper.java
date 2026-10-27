package com.hospital.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.system.entity.SysBackup;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysBackupMapper extends BaseMapper<SysBackup> {

    /** 备份历史：按时间倒序，只取最近 100 条 —— 更早的记录翻页也没人看 */
    @Select("""
            SELECT * FROM sys_backup
             ORDER BY create_time DESC
             LIMIT 100
            """)
    List<SysBackup> recent();
}
