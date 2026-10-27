package com.hospital.message.mapper;

import com.hospital.message.dto.WarningRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预警聚合查询。
 *
 * <p><b>为什么把别模块的表写在本模块的 Mapper 里</b>：预警天然是跨模块的
 * （药房、检验、公卫的异常都要收进来）。若各自去引对方的 Mapper，message 模块会
 * 反向依赖三四个业务模块，任何一处改动都会传导过来。
 * 这里只做<b>只读聚合</b>，SQL 写在自己家里，不产生 Java 依赖 ——
 * 与公卫模块「查疫苗批号不引 pharmacy」是同一个道理。</p>
 *
 * <p>所有查询都带 {@code deleted = 0} 且<b>只查未处理的数据</b>：
 * 已上报的传染病卡、已出库的近效期药品不该还挂在预警里。</p>
 */
public interface WarningMapper {

    /**
     * 库存预警：余量低于预警值（含已穿仓的 0）。
     */
    @Select("""
            SELECT s.id                AS biz_id,
                   d.drug_name         AS subject,
                   NULL                AS patient_name,
                   CONCAT('批号 ', s.batch_no) AS detail,
                   s.quantity          AS value,
                   s.warn_num          AS warn_value,
                   s.expire_date       AS due_date,
                   CAST(NULL AS SIGNED) AS days
              FROM biz_stock s
              JOIN biz_drug d ON d.id = s.drug_id AND d.deleted = 0
             WHERE s.deleted = 0
               AND s.warn_num IS NOT NULL
               AND s.quantity <= s.warn_num
             ORDER BY s.quantity ASC
             LIMIT #{limit}
            """)
    List<WarningRow> selectStockWarn(@Param("limit") int limit);

    /**
     * 效期预警：{@code days} 天内到期（含已过期）。
     *
     * <p>已过期的也一并纳入 —— 过期的药还在库里比快到期的更该被看见。</p>
     */
    @Select("""
            SELECT s.id          AS biz_id,
                   d.drug_name   AS subject,
                   NULL          AS patient_name,
                   CONCAT('批号 ', s.batch_no, '，余 ', s.quantity) AS detail,
                   DATEDIFF(s.expire_date, CURDATE()) AS value,
                   CAST(NULL AS SIGNED)  AS warn_value,
                   s.expire_date AS due_date,
                   DATEDIFF(s.expire_date, CURDATE()) AS days
              FROM biz_stock s
              JOIN biz_drug d ON d.id = s.drug_id AND d.deleted = 0
             WHERE s.deleted = 0
               AND s.expire_date IS NOT NULL
               AND s.expire_date <= DATE_ADD(CURDATE(), INTERVAL #{days} DAY)
             ORDER BY s.expire_date ASC
             LIMIT #{limit}
            """)
    List<WarningRow> selectDrugExpire(@Param("days") int days, @Param("limit") int limit);

    /**
     * 危急值：已发布的报告里带危急值标记的。
     *
     * <p>只取 {@code status = 1}（已发布）：草稿阶段的危急值还没经审核，
     * 提前推给临床会造成误判。</p>
     */
    @Select("""
            SELECT r.id                       AS biz_id,
                   IFNULL(a.exam_item, '检查报告') AS subject,
                   p.name                     AS patient_name,
                   r.report_no                AS detail,
                   CAST(NULL AS SIGNED)       AS value,
                   CAST(NULL AS SIGNED)       AS warn_value,
                   DATE(r.report_time)        AS due_date,
                   CAST(NULL AS SIGNED)       AS days
              FROM biz_exam_report r
              JOIN biz_exam_apply a ON a.id = r.apply_id AND a.deleted = 0
              JOIN biz_patient   p ON p.id = a.patient_id AND p.deleted = 0
             WHERE r.deleted = 0
               AND r.is_critical = 1
               AND r.status = 1
             ORDER BY r.report_time DESC
             LIMIT #{limit}
            """)
    List<WarningRow> selectCritical(@Param("limit") int limit);

    /**
     * 传染病报告卡超期未上报。
     *
     * <p>《传染病防治法》要求甲类 2 小时、乙类 24 小时内上报。
     * 这里按「填卡日早于今天仍未上报」判定 —— 隔夜未报的一律算超期，
     * 宁可多提醒，也不能让法定时限悄悄过去。</p>
     */
    @Select("""
            SELECT i.id                       AS biz_id,
                   i.disease_name             AS subject,
                   p.name                     AS patient_name,
                   IFNULL(i.icd_code, '未编码') AS detail,
                   DATEDIFF(CURDATE(), i.report_date) AS value,
                   CAST(NULL AS SIGNED)       AS warn_value,
                   i.report_date              AS due_date,
                   DATEDIFF(CURDATE(), i.report_date) AS days
              FROM biz_infectious_report i
              JOIN biz_patient p ON p.id = i.patient_id AND p.deleted = 0
             WHERE i.deleted = 0
               AND i.report_status = 0
               AND i.report_date < CURDATE()
             ORDER BY i.report_date ASC
             LIMIT #{limit}
            """)
    List<WarningRow> selectInfectiousOverdue(@Param("limit") int limit);

    /**
     * 疫苗逾期未种（含 {@code days} 天内即将到期）。
     */
    @Select("""
            SELECT v.id                       AS biz_id,
                   v.vaccine_name             AS subject,
                   p.name                     AS patient_name,
                   CONCAT('第 ', v.dose_no, ' 剂') AS detail,
                   DATEDIFF(CURDATE(), v.next_date) AS value,
                   CAST(NULL AS SIGNED)       AS warn_value,
                   v.next_date                AS due_date,
                   DATEDIFF(CURDATE(), v.next_date) AS days
              FROM biz_vaccination v
              JOIN biz_patient p ON p.id = v.patient_id AND p.deleted = 0
             WHERE v.deleted = 0
               AND v.next_date IS NOT NULL
               AND v.next_date <= DATE_ADD(CURDATE(), INTERVAL #{days} DAY)
             ORDER BY v.next_date ASC
             LIMIT #{limit}
            """)
    List<WarningRow> selectVaccineOverdue(@Param("days") int days, @Param("limit") int limit);
}
