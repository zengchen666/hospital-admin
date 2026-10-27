-- ============================================================================
-- 20 模块六 药品与药房管理：补采购权限点 + 打开菜单
--
-- 背景：采购单（建单 / 审核 / 入库）此前没有任何权限点，
--       只能挂在 pharmacy:stock:in 上凑合 —— 那是「动库存」的权限，
--       跟「发起一笔采购」不是一回事。采购审批是内控动作，必须有独立权限码。
--
-- 可重复执行
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 新增采购权限点
--    采购单「建单 / 作废」≠「审核」：不相容职务要求提单与审批由不同人做，
--    权限点必须分开，否则一个码两个人用，等于没分离。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_permission`
  (`id`, `perm_code`, `perm_name`, `perm_type`, `status`,
   `create_time`, `update_time`, `deleted`, `version`)
VALUES
  (3073, 'pharmacy:purchase:edit',  '采购单维护', 2, 1, NOW(), NOW(), 0, 0),
  (3074, 'pharmacy:purchase:audit', '采购单审核', 2, 1, NOW(), NOW(), 0, 0);

-- ---------------------------------------------------------------------------
-- 2. 授权
--    admin：全量
--    pharm（药师）：建单 + 入库，但**不给审核** —— 采购提单与审批分离
--    director（院长）：只给审核，不给建单
-- ---------------------------------------------------------------------------
SET @seq = (SELECT IFNULL(MAX(id), 9202) FROM `sys_role_permission`);

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('pharmacy:purchase:edit', 'pharmacy:purchase:audit')
   AND p.`deleted` = 0
 WHERE r.`role_code` = 'admin' AND r.`deleted` = 0;

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'pharmacy:purchase:edit' AND p.`deleted` = 0
 WHERE r.`role_code` = 'pharm' AND r.`deleted` = 0;

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'pharmacy:purchase:audit' AND p.`deleted` = 0
 WHERE r.`role_code` = 'director' AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 3. 打开模块六菜单（药品目录 / 库存管理 / 采购管理 / 门诊发药）
--
--    菜单判权统一用 pharmacy:query，页面内的按钮各自再判一次：
--    「采购管理」如果判 pharmacy:stock:in，院长有审核权却看不到菜单 ——
--    权限有了、入口没有，等于功能不存在。这与「门诊发药」菜单判 pharmacy:query、
--    发药按钮判 pharmacy:dispense 是同一套做法。
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` m
  JOIN (
    SELECT 701 id, 'pharmacy:query' p, 1 s UNION ALL
    SELECT 702,    'pharmacy:query',   1 UNION ALL
    SELECT 703,    'pharmacy:query',   1 UNION ALL
    SELECT 704,    'pharmacy:query',   1
  ) x ON x.id = m.id
   SET m.`perms` = x.p,
       m.`status` = x.s
 WHERE m.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
-- SELECT r.role_code, p.perm_code
--   FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
--   JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
--  WHERE p.perm_code LIKE 'pharmacy%'
--  ORDER BY r.role_code, p.perm_code;
