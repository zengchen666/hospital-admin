package com.hospital.inpatient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.inpatient.entity.BizBed;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BizBedMapper extends BaseMapper<BizBed> {

    /**
     * 占用床位：只有「空闲」才能占用成功。
     *
     * <p><b>这是防止一床住两人的唯一正确写法</b>。判断与更新在同一条 SQL 里，
     * 由行锁保证原子性；先 {@code selectById} 看状态再 {@code updateById} 必然翻车。</p>
     *
     * @return 1 占用成功；0 表示床位已被别人占用或不存在
     */
    @Update("""
            UPDATE biz_bed
               SET status = 1,
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int occupy(@Param("id") Long id);

    /** 释放床位：只有「占用中」才能释放，避免误把维修床改成空闲 */
    @Update("""
            UPDATE biz_bed
               SET status = 0,
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 1
               AND deleted = 0
            """)
    int release(@Param("id") Long id);
}
