-- =============================================================================
-- 权限补丁：检查检验查询权限
--
-- 背景：seed 有 exam:apply:add / exam:report:entry / exam:report:audit /
-- exam:critical:handle，缺查询码，检查单与报告列表无法授权（40301）。幂等。
-- =============================================================================

USE `hospital`;

INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3059, 'exam:query', '检查检验查询', 2, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `status`    = VALUES(`status`);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
VALUES (9059, 2001, 3059)
ON DUPLICATE KEY UPDATE `deleted` = 0;
