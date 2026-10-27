-- =============================================================================
-- 权限补丁：住院查询权限
--
-- 背景：seed 有 inpatient:admission:add / inpatient:order:add / inpatient:order:exec /
-- inpatient:nursing:add，缺查询码，住院列表与医嘱查询无法授权（40301）。幂等。
-- =============================================================================

USE `hospital`;

INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3060, 'inpatient:query', '住院查询', 2, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `status`    = VALUES(`status`);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
VALUES (9060, 2001, 3060)
ON DUPLICATE KEY UPDATE `deleted` = 0;
