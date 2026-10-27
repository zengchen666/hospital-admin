package com.hospital.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.system.entity.SysConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysConfigMapper extends BaseMapper<SysConfig> {

    @Select("SELECT * FROM sys_config WHERE config_key = #{configKey} AND deleted = 0 LIMIT 1")
    SysConfig selectByKey(@Param("configKey") String configKey);
}
