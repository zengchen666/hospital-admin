-- =============================================================================
-- 权限补丁：统计报表
--
-- 背景：需求文档模块十（统计报表）需要独立授权。报表能看到全院经营数据，
-- 不能挂在别的模块权限下——否则一个挂号员就能看到全院收入。幂等。
--
--   report:query    查看报表（院领导、科室主任、财务）
--   report:generate 生成/刷新预聚合快照（财务、信息科）
--
-- 注意：report:export（导出）seed 里已经有了（id=3046），这里不再重复定义。
-- 第一版曾试图新增 id=3062 的 report:export，但 perm_code 唯一键会让它
-- 被 3046 吃掉，结果 sys_role_permission 里留下一条指向不存在权限的悬空记录。
-- =============================================================================

USE `hospital`;

INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3061, 'report:query',    '报表查询',     2, NULL, 1, 1),
    (3063, 'report:generate', '报表快照生成', 2, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `status`    = VALUES(`status`);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
VALUES
    (9061, 2001, 3061),
    (9063, 2001, 3063)
ON DUPLICATE KEY UPDATE `deleted` = 0;
