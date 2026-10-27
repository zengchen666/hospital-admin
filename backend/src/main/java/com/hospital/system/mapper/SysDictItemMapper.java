package com.hospital.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.system.entity.SysDictItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    @Select("""
            SELECT * FROM sys_dict_item
             WHERE dict_type = #{dictType}
               AND deleted = 0
             ORDER BY sort ASC, id ASC
            """)
    List<SysDictItem> selectByType(@Param("dictType") String dictType);

    /**
     * 按「同一字典下同一取值」判重。
     *
     * <p>字典项的值重复会让下拉框出现两个选项但存进库里是同一个东西，
     * 使用者以为是两种选择。这是字典最典型的脏数据。</p>
     */
    @Select("""
            SELECT COUNT(*) FROM sys_dict_item
             WHERE dict_type = #{dictType}
               AND item_value = #{itemValue}
               AND deleted = 0
               AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    int countDuplicate(@Param("dictType") String dictType,
                       @Param("itemValue") String itemValue,
                       @Param("excludeId") Long excludeId);
}
