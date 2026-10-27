package com.hospital.publichealth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.publichealth.entity.BizVaccination;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface BizVaccinationMapper extends BaseMapper<BizVaccination> {

    /**
     * 该患者该疫苗已接种的最大剂次，用于自动推算下一剂次。
     */
    @Select("""
            SELECT IFNULL(MAX(dose_no), 0) FROM biz_vaccination
             WHERE patient_id = #{patientId}
               AND vaccine_name = #{vaccineName}
               AND deleted = 0
            """)
    int selectMaxDoseNo(@Param("patientId") Long patientId, @Param("vaccineName") String vaccineName);

    /**
     * 同一患者同一疫苗同一剂次是否已存在（防重复接种）。
     */
    @Select("""
            SELECT COUNT(1) FROM biz_vaccination
             WHERE patient_id = #{patientId}
               AND vaccine_name = #{vaccineName}
               AND dose_no = #{doseNo}
               AND deleted = 0
            """)
    int countSameDose(@Param("patientId") Long patientId,
                      @Param("vaccineName") String vaccineName,
                      @Param("doseNo") Integer doseNo);

    /**
     * 接种提醒：未来 {@code days} 天内到期的接种（含已逾期）。
     *
     * <p>上限取 {@code next_date <= CURDATE + days}，下限不设 ——
     * 逾期未种的比即将到期的更需要催，所以一并纳入。</p>
     */
    @Select("""
            SELECT * FROM biz_vaccination
             WHERE next_date IS NOT NULL
               AND next_date <= DATE_ADD(CURDATE(), INTERVAL #{days} DAY)
               AND deleted = 0
             ORDER BY next_date ASC
             LIMIT #{limit}
            """)
    List<BizVaccination> selectRemind(@Param("days") int days, @Param("limit") int limit);

    /**
     * 批号是否真实存在于疫苗库存中。
     *
     * <p>直接联查 biz_drug 与 biz_stock，不引入 pharmacy 模块的依赖 ——
     * 公卫只需确认「这个批号确实存在且是疫苗」，不该知道药房的内部结构。</p>
     */
    @Select("""
            SELECT COUNT(1)
              FROM biz_stock s
              JOIN biz_drug d ON d.id = s.drug_id AND d.deleted = 0
             WHERE s.batch_no = #{batchNo}
               AND d.category = '疫苗'
               AND s.deleted = 0
            """)
    int countVaccineBatch(@Param("batchNo") String batchNo);
}
