package com.hospital.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.registration.entity.BizRegistration;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizRegistrationMapper extends BaseMapper<BizRegistration> {

    /** 该排班已发出的最大排队号，用于生成下一个 */
    @Select("SELECT IFNULL(MAX(queue_no), 0) FROM biz_registration WHERE schedule_id = #{scheduleId}")
    int selectMaxQueueNo(@Param("scheduleId") Long scheduleId);

    /**
     * 当天最大挂号单号，用于序列懒初始化。
     *
     * <p>同样不带 {@code deleted = 0}：单号一旦用过就不该复用，否则会撞唯一索引。</p>
     */
    @Select("SELECT IFNULL(MAX(reg_no), '') FROM biz_registration WHERE reg_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxRegNo(@Param("prefix") String prefix);

    /** 该排班下某患者是否已有未退号的挂号（防止同一人重复占号） */
    @Select("""
            SELECT COUNT(1) FROM biz_registration
             WHERE schedule_id = #{scheduleId}
               AND patient_id = #{patientId}
               AND status IN (0, 1)
               AND deleted = 0
            """)
    int countActiveByScheduleAndPatient(@Param("scheduleId") Long scheduleId,
                                        @Param("patientId") Long patientId);
}
