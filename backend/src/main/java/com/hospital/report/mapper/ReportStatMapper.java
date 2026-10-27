package com.hospital.report.mapper;

import com.hospital.report.dto.DrugStatVO;
import com.hospital.report.dto.IncomeStatVO;
import com.hospital.report.dto.InpatientStatVO;
import com.hospital.report.dto.OutpatientStatVO;
import com.hospital.report.dto.PatientStatVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计报表聚合查询。
 *
 * <p><b>三条铁律</b>：
 * <ol>
 *   <li><b>聚合下推到 SQL</b>。绝不 {@code selectList} 把明细拉回 Java 再 stream 分组 ——
 *       那是把数据库该干的活搬到应用内存里，100 万行直接 OOM。</li>
 *   <li><b>每条 SQL 必须带时间范围</b>，由 Service 保证范围不超过 366 天。</li>
 *   <li><b>只查已生效的数据</b>：退号不算门诊量、未支付不算收入、作废处方不算绩效。</li>
 * </ol>
 *
 * <p>这里全部是原生 SQL 而非 MyBatis-Plus Wrapper：多表 JOIN + GROUP BY 用
 * lambda wrapper 写出来反而更难读，也更难让 DBA 看执行计划。</p>
 */
public interface ReportStatMapper {

    /**
     * 门诊量科室分布。
     *
     * <p>排除 {@code status = 3}（已退号）：退号意味着这次挂号从未发生，
     * 算进门诊量等于虚报工作量。</p>
     */
    @Select("""
            SELECT IFNULL(d.dept_name, '未分科室') AS deptName,
                   COUNT(r.id)                     AS regCount,
                   COUNT(DISTINCT r.patient_id)    AS patientCount,
                   IFNULL(SUM(r.reg_fee), 0)       AS regFeeAmount,
                   NULL                            AS statDate,
                   0                               AS ratio
              FROM biz_registration r
              LEFT JOIN sys_dept d ON d.id = r.dept_id
             WHERE r.deleted = 0
               AND r.status <> 3
               AND r.reg_time >= #{start}
               AND r.reg_time <  #{end}
               AND (#{deptId} IS NULL OR r.dept_id = #{deptId})
             GROUP BY r.dept_id, d.dept_name
             ORDER BY regCount DESC
            """)
    List<OutpatientStatVO> outpatientByDept(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("deptId") Long deptId);

    /** 门诊量按日趋势 */
    @Select("""
            SELECT NULL                         AS deptName,
                   COUNT(r.id)                  AS regCount,
                   COUNT(DISTINCT r.patient_id) AS patientCount,
                   IFNULL(SUM(r.reg_fee), 0)    AS regFeeAmount,
                   DATE(r.reg_time)             AS statDate,
                   0                            AS ratio
              FROM biz_registration r
             WHERE r.deleted = 0
               AND r.status <> 3
               AND r.reg_time >= #{start}
               AND r.reg_time <  #{end}
               AND (#{deptId} IS NULL OR r.dept_id = #{deptId})
             GROUP BY DATE(r.reg_time)
             ORDER BY statDate
            """)
    List<OutpatientStatVO> outpatientTrend(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("deptId") Long deptId);

    /**
     * 住院概况（单行）。
     *
     * <p>{@code avgStayDays} 只统计<b>区间内已出院</b>的患者，且 {@code DATEDIFF + 1}
     * —— 当天入院当天出院按医院口径算 1 天，不是 0 天。</p>
     *
     * <p>{@code availableBeds} 排除 {@code status = 2}（维修床）：维修床进分母
     * 会拉低使用率，正好掩盖床位紧张。</p>
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM biz_admission WHERE deleted = 0 AND status = 0) AS inHospitalCount,
              (SELECT COUNT(*) FROM biz_admission
                WHERE deleted = 0 AND admission_time >= #{start} AND admission_time < #{end}) AS admittedCount,
              (SELECT COUNT(*) FROM biz_admission
                WHERE deleted = 0 AND status = 1
                  AND discharge_time >= #{start} AND discharge_time < #{end}) AS dischargedCount,
              (SELECT COUNT(*) FROM biz_bed WHERE deleted = 0 AND status <> 2) AS availableBeds,
              (SELECT COUNT(*) FROM biz_bed WHERE deleted = 0 AND status = 1)  AS occupiedBeds,
              (SELECT IFNULL(AVG(DATEDIFF(discharge_time, admission_time) + 1), 0)
                 FROM biz_admission
                WHERE deleted = 0 AND status = 1 AND discharge_time IS NOT NULL
                  AND discharge_time >= #{start} AND discharge_time < #{end}) AS avgStayDays,
              (SELECT IFNULL(SUM(deposit), 0) FROM biz_admission WHERE deleted = 0 AND status = 0) AS depositBalance
            """)
    InpatientStatVO inpatientSummary(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 收入构成：按收费项目类型。
     *
     * <p>只统计 {@code pay_status = 1}（已支付）。数量扣掉已退数量
     * {@code quantity - refund_num}。</p>
     */
    @Select("""
            SELECT 'ITEM_TYPE'                                        AS dimension,
                   CAST(ci.item_type AS CHAR)                         AS dimensionKey,
                   CAST(ci.item_type AS CHAR)                         AS dimensionName,
                   IFNULL(SUM(ci.amount), 0)                          AS amount,
                   COUNT(DISTINCT ci.charge_id)                       AS chargeCount,
                   IFNULL(SUM(ci.quantity - IFNULL(ci.refund_num, 0)), 0) AS quantity,
                   NULL                                               AS visitCount,
                   NULL                                               AS prescAmount
              FROM biz_charge_item ci
              JOIN biz_charge c ON c.id = ci.charge_id AND c.deleted = 0
             WHERE ci.deleted = 0
               AND c.pay_status = 1
               AND c.charge_time >= #{start}
               AND c.charge_time <  #{end}
             GROUP BY ci.item_type
             ORDER BY amount DESC
            """)
    List<IncomeStatVO> incomeByItemType(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * 科室收入。
     *
     * <p>收费单本身没有科室字段，通过 {@code visit_id} 回挂号单拿 {@code dept_id}。
     * 住院收费没有 visit_id，会归到「未关联科室」—— 这是已知口径，
     * 不是 bug，等住院收费与就诊关联补齐后会自然归位。</p>
     */
    @Select("""
            SELECT 'DEPT'                             AS dimension,
                   CAST(r.dept_id AS CHAR)            AS dimensionKey,
                   IFNULL(d.dept_name, '未关联科室')   AS dimensionName,
                   IFNULL(SUM(c.actual_amount), 0)    AS amount,
                   COUNT(c.id)                        AS chargeCount,
                   NULL                               AS quantity,
                   NULL                               AS visitCount,
                   NULL                               AS prescAmount
              FROM biz_charge c
              LEFT JOIN biz_registration r ON r.id = c.visit_id
              LEFT JOIN sys_dept d ON d.id = r.dept_id
             WHERE c.deleted = 0
               AND c.pay_status = 1
               AND c.charge_time >= #{start}
               AND c.charge_time <  #{end}
             GROUP BY r.dept_id, d.dept_name
             ORDER BY amount DESC
            """)
    List<IncomeStatVO> incomeByDept(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * 医生绩效：接诊人次 + 开方金额。
     *
     * <p>处方只统计 {@code status IN (2, 3)}（已收费 / 已发药），
     * 排除 0 待审核（还没生效）与 4 已作废。用待审核处方算绩效，
     * 等于鼓励医生开没人抓的药。</p>
     */
    @Select("""
            SELECT 'DOCTOR'                              AS dimension,
                   CAST(mr.doctor_id AS CHAR)            AS dimensionKey,
                   IFNULL(u.real_name, CONCAT('医生#', mr.doctor_id)) AS dimensionName,
                   0                                     AS amount,
                   0                                     AS chargeCount,
                   NULL                                  AS quantity,
                   COUNT(DISTINCT mr.id)                 AS visitCount,
                   IFNULL(SUM(p.total_amount), 0)        AS prescAmount
              FROM biz_medical_record mr
              LEFT JOIN sys_user u ON u.id = mr.doctor_id
              LEFT JOIN biz_prescription p
                     ON p.visit_id = mr.visit_id AND p.deleted = 0 AND p.status IN (2, 3)
             WHERE mr.deleted = 0
               AND mr.visit_time >= #{start}
               AND mr.visit_time <  #{end}
             GROUP BY mr.doctor_id, u.real_name
             ORDER BY visitCount DESC, prescAmount DESC
             LIMIT #{topN}
            """)
    List<IncomeStatVO> doctorPerformance(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("topN") int topN);

    /**
     * 药品销售排行（按已发药）。
     *
     * <p><b>只统计「已发且未退」</b>：{@code d.status = 1} 是已发，{@code 2} 是已退。
     * 退掉的药如果还留在排行榜里，等于把没卖出去的算成卖出去的 ——
     * 排行越靠前的药越容易被多进货，误差会顺着采购一路放大。</p>
     *
     * <p>另外排除 {@code dispense_type = 3}（退药单本身）：退药单方向与发药相反，
     * 即使未来给它记了负数金额，也不该和正向销售混在同一张排行里。</p>
     */
    @Select("""
            SELECT CAST(di.drug_id AS CHAR)          AS drugId,
                   IFNULL(dr.drug_name, CONCAT('药品#', di.drug_id)) AS drugName,
                   dr.spec                           AS spec,
                   IFNULL(SUM(di.quantity), 0)       AS quantity,
                   IFNULL(SUM(di.amount), 0)         AS amount,
                   -- rank 是 MySQL 8 窗口函数保留字，必须加反引号
                   0                                 AS `rank`
              FROM biz_dispense_item di
              JOIN biz_dispense d ON d.id = di.dispense_id AND d.deleted = 0
              LEFT JOIN biz_drug dr ON dr.id = di.drug_id
             WHERE di.deleted = 0
               AND d.status = 1
               AND d.dispense_type <> 3
               AND d.dispense_time >= #{start}
               AND d.dispense_time <  #{end}
             GROUP BY di.drug_id, dr.drug_name, dr.spec
             ORDER BY amount DESC
             LIMIT #{topN}
            """)
    List<DrugStatVO> drugRanking(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 @Param("topN") int topN);

    /**
     * 患者年龄分布。
     *
     * <p>年龄实时算，不读缓存字段 —— 存下来的「年龄」从第二天开始就是错的。
     * 用 {@code TIMESTAMPDIFF(YEAR, birth_date, CURDATE())} 而不是
     * {@code DATEDIFF / 365}，后者在闰年边界上会差一天，分批算和整体算结果不一致。</p>
     */
    @Select("""
            SELECT 'AGE' AS dimension,
                   CASE
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 18 THEN '0-17'
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 40 THEN '18-39'
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 60 THEN '40-59'
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 80 THEN '60-79'
                     ELSE '80+'
                   END                          AS dimensionName,
                   CASE
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 18 THEN '0-17岁'
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 40 THEN '18-39岁'
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 60 THEN '40-59岁'
                     WHEN TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) < 80 THEN '60-79岁'
                     ELSE '80岁及以上'
                   END                          AS displayName,
                   COUNT(*)                     AS patientCount,
                   NULL                         AS statMonth
              FROM biz_patient
             WHERE deleted = 0
               AND birth_date IS NOT NULL
             GROUP BY dimensionName, displayName
             ORDER BY dimensionName
            """)
    List<PatientStatVO> patientAgeDistribution();

    /** 患者性别分布（gender：1 男 / 2 女 / 0 未知） */
    @Select("""
            SELECT 'GENDER' AS dimension,
                   CAST(gender AS CHAR) AS dimensionName,
                   CASE gender WHEN 1 THEN '男' WHEN 2 THEN '女' ELSE '未知' END AS displayName,
                   COUNT(*)             AS patientCount,
                   NULL                 AS statMonth
              FROM biz_patient
             WHERE deleted = 0
             GROUP BY gender
             ORDER BY gender
            """)
    List<PatientStatVO> patientGenderDistribution();

    /** 病种分布（按 ICD 编码，取 Top N） */
    @Select("""
            SELECT 'DISEASE'      AS dimension,
                   mr.icd_code    AS dimensionName,
                   MAX(mr.diagnosis) AS displayName,
                   COUNT(*)       AS patientCount,
                   NULL           AS statMonth
              FROM biz_medical_record mr
             WHERE mr.deleted = 0
               AND mr.visit_time >= #{start}
               AND mr.visit_time <  #{end}
               AND mr.icd_code IS NOT NULL
               AND mr.icd_code <> ''
             GROUP BY mr.icd_code
             ORDER BY patientCount DESC
             LIMIT #{topN}
            """)
    List<PatientStatVO> diseaseRanking(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("topN") int topN);

    /**
     * 建档趋势（按月）。
     *
     * <p><b>GROUP BY 必须按别名写</b>：{@code statMonth} 是
     * {@code DATE(CONCAT(DATE_FORMAT(create_time,'%Y-%m'),'-01'))}，
     * 与 {@code displayName} 的 {@code DATE_FORMAT(...)} 不是同一个表达式。
     * 若 GROUP BY 只写后者，MySQL 在 {@code only_full_group_by} 下会直接拒绝
     * —— 它认不出这两个表达式函数等价（本地开发库默认开，线上更严）。</p>
     */
    @Select("""
            SELECT 'NEW'                                AS dimension,
                   NULL                                 AS dimensionName,
                   DATE_FORMAT(create_time, '%Y-%m')    AS displayName,
                   COUNT(*)                             AS patientCount,
                   DATE(CONCAT(DATE_FORMAT(create_time, '%Y-%m'), '-01')) AS statMonth
              FROM biz_patient
             WHERE deleted = 0
               AND create_time >= #{start}
               AND create_time <  #{end}
             GROUP BY displayName, statMonth
             ORDER BY statMonth
            """)
    List<PatientStatVO> newPatientTrend(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
