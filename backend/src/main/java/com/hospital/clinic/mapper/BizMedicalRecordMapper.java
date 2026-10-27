package com.hospital.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.clinic.entity.BizMedicalRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizMedicalRecordMapper extends BaseMapper<BizMedicalRecord> {

    /** 按就诊取病历（uk_record_visit 保证最多一条） */
    @Select("""
            SELECT * FROM biz_medical_record
             WHERE visit_id = #{visitId} AND deleted = 0
             LIMIT 1
            """)
    BizMedicalRecord selectByVisitId(@Param("visitId") Long visitId);
}
