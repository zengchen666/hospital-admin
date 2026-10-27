package com.hospital.publichealth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.publichealth.entity.BizHealthRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizHealthRecordMapper extends BaseMapper<BizHealthRecord> {

    /**
     * 取库中最大档案号，用于序列懒初始化。
     *
     * <p><b>刻意不加 {@code deleted = 0}</b>：{@code uk_health_record_no} 唯一索引包含
     * 已逻辑删除的行，档案号一旦用过就不能再复用，否则迟早撞 1062。</p>
     */
    @Select("SELECT IFNULL(MAX(record_no), '') FROM biz_health_record WHERE record_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxRecordNo(@Param("prefix") String prefix);

    /** 该患者是否已有档案（一人一份） */
    @Select("""
            SELECT COUNT(1) FROM biz_health_record
             WHERE patient_id = #{patientId} AND deleted = 0
            """)
    int countByPatient(@Param("patientId") Long patientId);
}
