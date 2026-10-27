-- ============================================================================
-- 医疗后台管理系统 — 数据库初始化
-- 版本    : v1.1
-- 日期    : 2026-10-26
-- 依据    : 《接口,字段,原型图文档.md》v1.1（55 张表）
-- 数据库  : MySQL 8.0+ / InnoDB / utf8mb4
-- ============================================================================
-- 执行顺序（必须按编号，不可打乱）
--   00_create_database.sql      建库
--   01_sys.sql                  系统管理     10 张
--   02_patient_reg_clinic.sql   患者/挂号/门诊 9 张
--   03_pharmacy.sql             药品与药房    9 张
--   04_finance.sql              收费与财务    6 张
--   05_inpatient_exam.sql       住院/检查检验 8 张
--   06_public_health.sql        公共卫生      5 张
--   07_report_message_audit.sql 报表/消息/审计 8 张
--   08_seed.sql                 初始数据
--                         合计  55 张业务表
-- ============================================================================
-- 设计约定（与文档 1.4 / 1.5 一致）
--   1. 不使用数据库外键约束：利于分表与数据迁移，关系由应用层保证；
--      但每个外键字段都建了普通索引，避免全表扫描。
--   2. 主键 id 为 BIGINT 雪花 ID，不使用 AUTO_INCREMENT（为分表留余量）。
--   3. 公共字段 create_by / create_time / update_by / update_time /
--      deleted / version 除日志与快照表外必须存在。
--   4. 金额一律 DECIMAL(12,2)，禁止 FLOAT / DOUBLE。
--   5. 枚举一律 TINYINT + sys_dict，字段注释里写清取值含义。
--   6. 所有表 ENGINE=InnoDB，字符集 utf8mb4。
-- ============================================================================
-- 快速执行（Windows cmd，在 sql 目录下）
--   for %f in (00*.sql 01*.sql 02*.sql 03*.sql 04*.sql 05*.sql 06*.sql 07*.sql 08*.sql) do mysql -uroot -p hospital < %f
-- 或在 MySQL 客户端内依次 source 每个文件。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `hospital`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `hospital`;
