-- ============================================================================
-- 23 模块九 公共卫生：打开菜单 + 补齐岗位授权
--
-- 背景：
--   1) 菜单 1001~1004 全部 status=0，模块九在侧边栏里根本不显示；
--   2) doctor 有 health:checkup:add / health:checkup:report，但 nurse 只有
--      health:record:edit + health:vaccine:add —— 接种登记归护士、体检归医生，
--      这条分工是对的，不用动；
--   3) 缺一个「公卫专员」岗位：既不是医生也不是护士，专门做档案与传染病随访。
--      一期 7 个角色里没有它，实际医院公卫科是独立编制。
--
-- 可重复执行
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 打开模块九菜单
--    统一判 health:query，页内按钮各自判权：
--      档案维护（health:record:edit）/ 接种登记（health:vaccine:add）
--      传染病上报（health:infectious:report）/ 体检登记（health:checkup:add）
--      体检报告（health:checkup:report）/ 套餐维护（health:package:edit）
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` m
  JOIN (
    SELECT 1001 id, 'health:query' p, 1 s UNION ALL
    SELECT 1002,    'health:query',   1 UNION ALL
    SELECT 1003,    'health:query',   1 UNION ALL
    SELECT 1004,    'health:query',   1
  ) x ON x.id = m.id
   SET m.`perms` = x.p,
       m.`status` = x.s
 WHERE m.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 2. 公卫专员岗位（2009）+ 账号 ph01
--    只给「查询 + 档案维护 + 接种登记 + 传染病上报」，
--    不给体检登记与出报告 —— 体检是有创/有费用的医疗行为，归医生。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role`
  (`id`, `role_name`, `role_code`, `remark`, `status`,
   `create_time`, `update_time`, `deleted`, `version`)
VALUES
  (2009, '公卫专员', 'ph', '健康档案、疫苗接种、传染病上报', 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO `sys_user`
  (`id`, `username`, `password`, `real_name`, `gender`, `dept_id`, `role_id`, `status`)
VALUES
  (9, 'ph01', '{noop}123456', '吴公卫', 2, 1005, 2009, 1);

INSERT IGNORE INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES (5009, 9, 2009);

SET @seq = (SELECT IFNULL(MAX(id), 9500) FROM `sys_role_permission`);

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p
    ON p.`perm_code` IN ('health:query', 'health:record:edit',
                         'health:vaccine:add', 'health:infectious:report')
   AND p.`deleted` = 0
 WHERE r.`role_code` = 'ph' AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 3. 护士补 health:query（接种登记页要能查到档案）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'health:query' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('nurse', 'doctor') AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
-- SELECT id, menu_name, perms, status FROM sys_menu WHERE id BETWEEN 1001 AND 1004;
-- SELECT r.role_code, p.perm_code FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
--   JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
--  WHERE p.perm_code LIKE 'health%' ORDER BY r.role_code, p.perm_code;
