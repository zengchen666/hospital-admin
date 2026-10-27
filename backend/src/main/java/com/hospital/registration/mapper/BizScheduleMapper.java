package com.hospital.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.registration.entity.BizSchedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BizScheduleMapper extends BaseMapper<BizSchedule> {

    /**
     * 扣减一个号源。
     *
     * <p><b>这是防超卖的唯一正确写法</b>：把「判断有余量」和「扣减」放进同一条 SQL，
     * 由数据库行锁保证原子性。绝不能先 {@code selectById} 判断 {@code remainNum > 0}
     * 再 {@code updateById} —— 两个线程同时读到 1，就会卖出 2 个号。</p>
     *
     * @return 1 表示扣减成功；0 表示号源已空、已停诊或排班不存在
     */
    @Update("""
            UPDATE biz_schedule
               SET remain_num = remain_num - 1,
                   update_time = NOW()
             WHERE id = #{id}
               AND remain_num > 0
               AND status = 1
               AND deleted = 0
            """)
    int decreaseStock(@Param("id") Long id);

    /**
     * 回补一个号源（退号时调用）。
     *
     * <p>用 {@code LEAST(remain_num + 1, total_num)} 兜住上界：
     * 即使因为重试/重复调用多补了一次，也不会出现「剩余号源大于总号源」这种脏数据。</p>
     */
    @Update("""
            UPDATE biz_schedule
               SET remain_num = LEAST(remain_num + 1, total_num),
                   update_time = NOW()
             WHERE id = #{id}
               AND deleted = 0
            """)
    int increaseStock(@Param("id") Long id);
}
