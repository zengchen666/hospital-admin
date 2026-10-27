package com.hospital.inpatient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.inpatient.entity.BizMedicalOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BizMedicalOrderMapper extends BaseMapper<BizMedicalOrder> {

    /**
     * 停止医嘱：未执行/已执行 → 已停止，并记录停止时间。
     *
     * <p>带原状态条件，避免重复停止或「已停止的又被改成停止」导致停止时间被刷新。</p>
     */
    @Update("""
            UPDATE biz_medical_order
               SET status = 2,
                   stop_time = NOW(),
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status IN (0, 1)
               AND deleted = 0
            """)
    int stop(@Param("id") Long id, @Param("operatorId") Long operatorId);

    /** 执行医嘱：未执行 → 已执行（临时医嘱执行后即完成） */
    @Update("""
            UPDATE biz_medical_order
               SET status = 1,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int execute(@Param("id") Long id, @Param("operatorId") Long operatorId);
}
