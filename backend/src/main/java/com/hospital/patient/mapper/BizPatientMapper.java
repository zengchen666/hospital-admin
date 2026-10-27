package com.hospital.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.patient.entity.BizPatient;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizPatientMapper extends BaseMapper<BizPatient> {

    /**
     * 取库中最大患者编号，用于序列的懒初始化。
     *
     * <p><b>刻意不加 {@code deleted = 0}</b>：{@code uk_patient_no} 唯一索引包含
     * 已逻辑删除的行，编号一旦用过就不能再用，否则重建时必然撞 1062。</p>
     */
    @Select("SELECT IFNULL(MAX(patient_no), '') FROM biz_patient")
    String selectMaxPatientNo();

    /** 按身份证 hash 查重（只查未删除的） */
    @Select("""
            SELECT COUNT(1) FROM biz_patient
             WHERE id_card_hash = #{idCardHash} AND deleted = 0
            """)
    int countByIdCardHash(@Param("idCardHash") String idCardHash);

    /**
     * 该患者是否还有未结束的挂号（待就诊 / 就诊中）。
     *
     * <p>删档前必须拦：否则病历、处方、收费单会指向一个已删除的患者，
     * 后面查单据全是空名字。跨表查询放在这里，是为了避免 patient 模块
     * 直接依赖 registration 的 Service。</p>
     */
    @Select("""
            SELECT COUNT(1) FROM biz_registration
             WHERE patient_id = #{patientId}
               AND status IN (0, 1)
               AND deleted = 0
            """)
    int countActiveRegistration(@Param("patientId") Long patientId);

    /** 同一身份证查重时排除自身（修改场景） */
    @Select("""
            SELECT COUNT(1) FROM biz_patient
             WHERE id_card_hash = #{idCardHash} AND id <> #{excludeId} AND deleted = 0
            """)
    int countByIdCardHashExcluding(@Param("idCardHash") String idCardHash,
                                   @Param("excludeId") Long excludeId);
}
