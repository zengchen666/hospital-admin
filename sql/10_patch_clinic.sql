-- =============================================================================
-- 权限补丁：门诊模块查询权限
--
-- 背景：08_seed.sql 只给了 clinic:receive / clinic:prescribe / clinic:presc:audit，
-- 缺查询码，导致「就诊列表」「处方分页」无法授权（40301）。本脚本幂等。
-- =============================================================================

USE `hospital`;

INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3056, 'clinic:query', '门诊查询', 2, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `status`    = VALUES(`status`);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
VALUES (9056, 2001, 3056)
ON DUPLICATE KEY UPDATE `deleted` = 0;
