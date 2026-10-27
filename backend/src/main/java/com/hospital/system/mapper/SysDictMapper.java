package com.hospital.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.system.entity.SysDict;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysDictMapper extends BaseMapper<SysDict> {

    /**
     * 按类型查字典。
     *
     * <p>不加 {@code status = 1}：停用状态由服务层按需过滤。
     * 硬编码在 SQL 里的话，将来「管理页要看到停用的字典」就又得写第二份查询。</p>
     */
    @Select("SELECT * FROM sys_dict WHERE dict_type = #{dictType} AND deleted = 0 LIMIT 1")
    SysDict selectByType(@Param("dictType") String dictType);
}
