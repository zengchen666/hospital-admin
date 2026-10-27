-- =============================================================================
-- 模块十 统计报表：表结构扩展 + 菜单启用 + 权限补齐
--
-- 本补丁做三件事：
--   1. sys_report_config 增加「统计维度」与「展示形式」两列
--      —— 自定义报表要能按维度检索，维度必须是独立列而不是 JSON 里的字段
--   2. 菜单 1101~1104 从「已建未启用」改为启用，并补 1105 患者分析 / 1106 自定义报表
--   3. 新增 report:config（改配置）权限，并把 report:query / export / generate
--      按角色矩阵补齐
--
-- 幂等：列用 IF NOT EXISTS 判断（存储过程模拟），其余一律 INSERT IGNORE /
--       ON DUPLICATE KEY UPDATE。
-- =============================================================================

USE `hospital`;

-- ---------------------------------------------------------------------------
-- 1. 表结构扩展
-- ---------------------------------------------------------------------------
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后
-- 拼接动态 SQL。直接写 ALTER 在重复执行时会报 1060 并中断整段脚本。
SET @exist_dim = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sys_report_config'
                     AND COLUMN_NAME = 'dimension');
SET @sql_dim = IF(@exist_dim = 0,
    'ALTER TABLE sys_report_config ADD COLUMN `dimension` VARCHAR(30) DEFAULT NULL COMMENT ''统计维度：dept/date/itemType/doctor/drug/age/gender/disease/new/summary'' AFTER `report_type`',
    'SELECT ''column dimension already exists'' AS info');
PREPARE stmt FROM @sql_dim; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist_chart = (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'sys_report_config'
                       AND COLUMN_NAME = 'chart_type');
SET @sql_chart = IF(@exist_chart = 0,
    'ALTER TABLE sys_report_config ADD COLUMN `chart_type` VARCHAR(20) NOT NULL DEFAULT ''table'' COMMENT ''展示形式：table/bar/line/pie'' AFTER `dimension`',
    'SELECT ''column chart_type already exists'' AS info');
PREPARE stmt FROM @sql_chart; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 菜单：启用 + 补两个
--
-- 统计报表父菜单 1100 已启用；四个子菜单此前 status=0（建了但没开），
-- 前端动态路由因此拉不到它们，表现为「菜单里能看到统计报表却点不进去」。
-- ---------------------------------------------------------------------------
UPDATE `sys_menu`
   SET `perms` = 'report:query', `status` = 1, `visible` = 1
 WHERE `id` IN (1101, 1102, 1103, 1104);

INSERT INTO `sys_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`,
     `perms`, `icon`, `sort`, `visible`, `status`)
VALUES
    (1105, 1100, '患者分析',   2, '/report/patient', 'report/patient/index', 'report:query', 'chart', 5, 1, 1),
    (1106, 1100, '自定义报表', 2, '/report/custom',  'report/custom/index',  'report:query', 'chart', 6, 1, 1)
ON DUPLICATE KEY UPDATE
    `menu_name` = VALUES(`menu_name`),
    `perms`     = VALUES(`perms`),
    `component` = VALUES(`component`),
    `status`    = 1,
    `visible`   = 1;

-- ---------------------------------------------------------------------------
-- 3. 权限：新增 report:config
--
-- 权限分四档，粒度按「谁能看 / 谁能带走 / 谁能改口径 / 谁能钉死历史」拆：
--   report:query    看报表（院领导、科室主任、财务、医生）
--   report:export   导出文件（院领导、财务）—— 导出后数据离开系统，不可回收
--   report:config   改自定义报表配置（仅 admin）
--   report:generate 生成预聚合快照（admin、财务）
-- ---------------------------------------------------------------------------
-- ⚠️ id 不能用 3064：它已经是 health:query（见 17_patch_publichealth.sql）。
--    第一版就是踩了这个坑 —— ON DUPLICATE KEY UPDATE 命中主键后
--    把 health:query 的名字改成了「报表配置维护」，公卫模块的权限名当场错乱。
--
--    因此这里额外加一道保险：只有 perm_code 一致时才更新名字，
--    id 被别的权限占用时保持原样不动，绝不静默改写他人数据。
INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3075, 'report:config', '报表配置维护', 2, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = IF(`perm_code` = VALUES(`perm_code`), VALUES(`perm_name`), `perm_name`),
    `status`    = IF(`perm_code` = VALUES(`perm_code`), VALUES(`status`), `status`);

SET @seq = (SELECT IFNULL(MAX(id), 9500) FROM `sys_role_permission`);

-- 3.1 查询权：管理层 + 财务 + 医生（医生看自己科室的接诊统计）
INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'report:query' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director', 'finance', 'doctor') AND r.`deleted` = 0;

-- 3.2 导出权：管理层 + 财务
INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'report:export' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director', 'finance') AND r.`deleted` = 0;

-- 3.3 配置权：仅 admin（改配置等于改别人看到的数字口径）
INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'report:config' AND p.`deleted` = 0
 WHERE r.`role_code` = 'admin' AND r.`deleted` = 0;

-- 3.4 快照生成权：admin + 财务（日结/月结要对得上历史快照）
INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'report:generate' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'finance') AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
-- SELECT id, menu_name, perms, status FROM sys_menu WHERE id BETWEEN 1100 AND 1106;
-- SELECT r.role_code, p.perm_code FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
--   JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
--  WHERE p.perm_code LIKE 'report%' ORDER BY r.role_code, p.perm_code;
