-- ============================================================================
-- 22 模块八 收费与财务：建日结表 + 打开菜单 + 补齐岗位授权
--
-- 背景：
--   1) 《接口文档》只给了 GET /api/finance/daily 的汇总口径，没有落库表。
--      但「提交日结」这个动作必须有凭据 —— 短款 200 块是谁交的班、
--      当时差异说明写的什么，第二天查的就是这条记录。只写审计日志不够：
--      审计日志是流水，查「这个月哪几天有差异」要翻全表；这里一天一行，直接查；
--   2) 菜单 901~905 全部 status=0，模块八在侧边栏里根本不显示；
--   3) finance 岗位没有 finance:charge:add，收费员能收费但财务岗收不了，
--      演示「日结」时得先借收费员的号。
--
-- 可重复执行
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 日结记录表（一天一条，重复提交覆盖）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_daily_settle` (
  `id`            BIGINT       NOT NULL COMMENT '主键',
  `settle_date`   DATE         NOT NULL COMMENT '结账日期',
  `charge_count`  INT          NOT NULL DEFAULT 0     COMMENT '收费笔数',
  `total_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '收费总额',
  `refund_count`  INT          NOT NULL DEFAULT 0     COMMENT '退费笔数',
  `refund_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退费总额',
  `expected_cash` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '应有现金=现金收入-现金退费',
  `actual_cash`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实点现金',
  `diff_amount`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '差异=实点-应有',
  `diff_remark`   VARCHAR(255) DEFAULT NULL           COMMENT '差异说明，差异非0必填',
  `operator_id`   BIGINT       DEFAULT NULL           COMMENT '交班人',
  `submit_time`   DATETIME     DEFAULT NULL           COMMENT '提交时间',
  `create_by`     BIGINT       DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`     BIGINT       DEFAULT NULL,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  `version`       INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_settle_date` (`settle_date`),
  KEY `idx_daily_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日结记录';

-- ---------------------------------------------------------------------------
-- 2. 打开模块八菜单
--    统一判 finance:query，页内按钮各自判权：
--      收费（finance:charge:add）/ 退费申请（finance:refund:apply）
--      退费审核（finance:refund:audit）/ 发票作废（finance:invoice:void）
--      提交日结（finance:daily:submit）/ 调价（finance:treat:edit）
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` m
  JOIN (
    SELECT 901 id, 'finance:query' p, 1 s UNION ALL
    SELECT 902,    'finance:query',   1 UNION ALL
    SELECT 903,    'finance:query',   1 UNION ALL
    SELECT 904,    'finance:query',   1 UNION ALL
    SELECT 905,    'finance:query',   1
  ) x ON x.id = m.id
   SET m.`perms` = x.p,
       m.`status` = x.s
 WHERE m.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 3. 岗位授权：
--    finance 补 finance:charge:add（日结演示不需要再借收费员的号）
--    finance 补 finance:invoice:void（作废是冲抵收入的动作，归财务口）
-- ---------------------------------------------------------------------------
SET @seq = (SELECT IFNULL(MAX(id), 9400) FROM `sys_role_permission`);

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('finance:charge:add', 'finance:invoice:void')
       AND p.`deleted` = 0
 WHERE r.`role_code` = 'finance' AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
-- SELECT id, menu_name, perms, status FROM sys_menu WHERE id BETWEEN 901 AND 905;
-- SELECT r.role_code, p.perm_code FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
--   JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
--  WHERE p.perm_code LIKE 'finance%' ORDER BY r.role_code, p.perm_code;
