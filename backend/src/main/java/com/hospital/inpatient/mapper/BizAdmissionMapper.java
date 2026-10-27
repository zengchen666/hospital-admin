package com.hospital.inpatient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.inpatient.entity.BizAdmission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BizAdmissionMapper extends BaseMapper<BizAdmission> {

    @Select("SELECT IFNULL(MAX(admission_no), '') FROM biz_admission WHERE admission_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxAdmissionNo(@Param("prefix") String prefix);

    /**
     * 该患者是否已在院（0 在院）。
     *
     * <p>同一个患者不能同时有两条在院记录 —— 否则床位、押金、医嘱全乱。</p>
     */
    @Select("""
            SELECT COUNT(1) FROM biz_admission
             WHERE patient_id = #{patientId}
               AND status = 0
               AND deleted = 0
            """)
    int countInHospital(@Param("patientId") Long patientId);

    /**
     * 出院：0 在院 → 1 出院，同时写入出院时间。
     *
     * @return 1 成功；0 表示已出院或被并发出院
     */
    @Update("""
            UPDATE biz_admission
               SET status = 1,
                   discharge_time = NOW(),
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int discharge(@Param("id") Long id, @Param("operatorId") Long operatorId);

    /**
     * 预交金充值：金额只增不减，扣费走收费模块并生成独立流水。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE biz_admission
               SET deposit = deposit + #{amount},
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int addDeposit(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount,
                   @Param("operatorId") Long operatorId);

    /**
     * 转科：换科室 / 换医生，同时清空床位（原床位由 Service 释放）。
     *
     * <p>{@code status = 0} 条件不能省：已出院的记录不能被转科，
     * 否则会凭空多出一个「在院」患者。</p>
     *
     * @return 1 成功；0 表示已出院或被并发修改
     */
    @Update("""
            UPDATE biz_admission
               SET dept_id = #{deptId},
                   doctor_id = #{doctorId},
                   bed_id = NULL,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int transfer(@Param("id") Long id, @Param("deptId") Long deptId,
                 @Param("doctorId") Long doctorId, @Param("operatorId") Long operatorId);
}
