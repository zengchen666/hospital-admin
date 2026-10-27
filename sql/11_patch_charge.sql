-- =============================================================================
-- 权限补丁：收费 / 发药查询权限
--
-- 背景：seed 只有 finance:charge:add、pharmacy:dispense 等操作码，
-- 缺查询码，收费单与发药单列表无法授权（40301）。本脚本幂等。
-- =============================================================================

USE `hospital`;

INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3057, 'finance:query',  '收费查询', 2, NULL, 1, 1),
    (3058, 'pharmacy:query', '药房查询', 2, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `status`    = VALUES(`status`);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
VALUES
    (9057, 2001, 3057),
    (9058, 2001, 3058)
ON DUPLICATE KEY UPDATE `deleted` = 0;
