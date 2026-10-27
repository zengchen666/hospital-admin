-- ============================================================================
-- 18 消息与通知模块补丁：权限点 + 菜单修正 + 角色授权
-- 依赖：07_report_message_audit.sql（表）、08_seed.sql（角色/菜单）
-- 可重复执行
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 菜单结构修正
--    seed 里 1200「消息中心」是 menu_type=2（菜单）却又有子菜单 1201，
--    前端按「有子菜单即目录」处理时，/message 自身会被跳过 ——
--    结果站内消息页进不去。这里把它改成目录（menu_type=1）并补齐子菜单。
-- ---------------------------------------------------------------------------
UPDATE `sys_menu`
   SET `menu_type` = 1, `component` = NULL, `perms` = NULL
 WHERE `id` = 1200 AND `deleted` = 0;

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `status`)
VALUES
  (1202, 1200, '站内消息', 2, '/message/list',   'message/list/index',   'message:query',      'Message',   1, 1),
  (1203, 1200, '短信记录', 2, '/message/sms',    'message/sms/index',    'message:query',      'ChatDotRound', 3, 1),
  (1204, 1200, '预警中心', 2, '/message/warning','message/warning/index','message:query',      'WarningFilled', 4, 1)
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `component` = VALUES(`component`),
  `perms`     = VALUES(`perms`);

UPDATE `sys_menu` SET `sort` = 2 WHERE `id` = 1201 AND `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 2. 新增权限点（seed 已有 3047 message:notice:publish）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `menu_id`, `perm_type`, `data_scope`, `status`)
VALUES
  (3068, 'message:query',        '消息查询',   NULL, 2, 3, 1),
  (3069, 'message:sms:send',     '发送短信',   NULL, 2, 3, 1),
  (3070, 'message:notice:edit',  '公告编辑',   NULL, 2, 3, 1),
  (3071, 'message:warning:scan', '预警扫描',   NULL, 2, 3, 1)
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ---------------------------------------------------------------------------
-- 3. 角色授权
--    ⚠️ sys_role_permission.id 没有自增，必须显式给值：
--       不给值时非严格模式下被填成 0，第二条起全撞主键，
--       配合 INSERT IGNORE 就是「只有第一行插进去」。
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission` WHERE `id` = 0;
SET @seq := (SELECT IFNULL(MAX(`id`), 9100) FROM `sys_role_permission`);

-- 消息查询：所有角色都要能看消息中心（消息是推给个人的，按 receiver_id 隔离）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'message:query' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director', 'doctor', 'nurse', 'cashier', 'pharm', 'finance')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 公告发布/编辑：管理员、院长
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('message:notice:publish', 'message:notice:edit')
       AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 发送短信：管理员、院长（短信有成本且涉及患者隐私，不给一线）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'message:sms:send' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 预警扫描：管理员、院长、药房（库存/效期预警是药房要处理的）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'message:warning:scan' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director', 'pharm')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 5. 菜单判权码修正
--    seed 里 1201「公告管理」的 perms 是 message:notice:list，
--    但 sys_permission 里根本没有这个权限码 —— SysMenuService 按
--    「用户权限是否包含 menu.perms」过滤菜单，于是这个菜单对谁都不显示。
--    统一改成 message:notice:edit（写操作只留一个权限码，
--    与 seed 的 message:notice:publish 二选一，避免「能发不能改」）。
-- ---------------------------------------------------------------------------
UPDATE `sys_menu`
   SET `perms` = 'message:notice:edit'
 WHERE `id` = 1201 AND `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 6. 子菜单判权码对齐各自的接口权限
--    初版把 1202/1203/1204 全部写成 message:query，结果医生能看到
--    「短信记录」「预警中心」两个菜单，点进去调接口却被 40301 拦掉 ——
--    菜单可见性与接口权限必须是同一个码，否则就是在给用户指一条走不通的路。
--    站内消息保持 message:query（消息推给个人，按 receiver_id 隔离，全员可见）。
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` SET `perms` = 'message:sms:send'     WHERE `id` = 1203 AND `deleted` = 0;
UPDATE `sys_menu` SET `perms` = 'message:warning:scan' WHERE `id` = 1204 AND `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 7. 自检
-- ---------------------------------------------------------------------------
-- SELECT id, parent_id, menu_name, menu_type, path FROM sys_menu WHERE id BETWEEN 1200 AND 1204;
-- SELECT r.role_code, COUNT(rp.permission_id) c FROM sys_role r
--   LEFT JOIN sys_role_permission rp ON rp.role_id=r.id AND rp.deleted=0
--  GROUP BY r.role_code ORDER BY c DESC;
