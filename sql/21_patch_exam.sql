-- ============================================================================
-- 21 模块七 检查检验：补技师角色 + 补字段 + 打开菜单
--
-- 背景：
--   1) 7 个角色里没有「检验技师」—— exam:report:entry 只授给了 admin，
--      整个模块七只能靠超级管理员演示，岗位分工是空的；
--   2) biz_exam_report 只有「是否危急值」，没有处理状态字段 ——
--      危急值发布后没有闭环，谁处理的、什么时候处理的无从查证；
--   3) biz_exam_apply 没有条码字段，「打印样本条码」每次只能拿申请号临时凑，
--      重打一次可能对不上。
--
-- 可重复执行
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 字段补齐
-- ---------------------------------------------------------------------------
ALTER TABLE `biz_exam_apply`
  ADD COLUMN `barcode_no` VARCHAR(40) DEFAULT NULL COMMENT '样本条码号，首次打印时生成，重打保持同一号' AFTER `apply_no`;

ALTER TABLE `biz_exam_report`
  ADD COLUMN `handle_status` TINYINT      NOT NULL DEFAULT 0  COMMENT '危急值处理：0未处理 1已处理' AFTER `is_critical`,
  ADD COLUMN `handler_id`    BIGINT       DEFAULT NULL        COMMENT '危急值处理人' AFTER `handle_status`,
  ADD COLUMN `handle_time`   DATETIME     DEFAULT NULL        COMMENT '危急值处理时间' AFTER `handler_id`,
  ADD COLUMN `handle_remark` VARCHAR(255) DEFAULT NULL        COMMENT '危急值处理措施' AFTER `handle_time`;

-- ---------------------------------------------------------------------------
-- 2. 检验技师角色 + 账号
--    技师只给「录入」，不给「审核发布」—— 报告审核是另一个岗位，
--    自己录自己发等于没有复核。审核权给 admin 与 director。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role`
  (`id`, `role_name`, `role_code`, `remark`, `status`,
   `create_time`, `update_time`, `deleted`, `version`)
VALUES
  (2008, '检验技师', 'tech', '检查检验结果录入与样本管理', 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO `sys_user`
  (`id`, `username`, `password`, `real_name`, `gender`, `dept_id`, `role_id`, `status`)
VALUES
  (8, 'tech01', '{noop}123456', '陈技师', 1, 1004, 2008, 1);

INSERT IGNORE INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES (5008, 8, 2008);

-- ---------------------------------------------------------------------------
-- 3. 授权
--    tech   : exam:query + exam:report:entry（只录入，不发布）
--    director: 补 exam:report:audit（管理层复核发布）
-- ---------------------------------------------------------------------------
SET @seq = (SELECT IFNULL(MAX(id), 9300) FROM `sys_role_permission`);

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('exam:query', 'exam:report:entry') AND p.`deleted` = 0
 WHERE r.`role_code` = 'tech' AND r.`deleted` = 0;

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'exam:report:audit' AND p.`deleted` = 0
 WHERE r.`role_code` = 'director' AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 4. 打开模块七菜单
--    统一判 exam:query，页内按钮各自判权
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` m
  JOIN (
    SELECT 801 id, 'exam:query' p, 1 s UNION ALL
    SELECT 802,    'exam:query',   1 UNION ALL
    SELECT 803,    'exam:query',   1
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
--  WHERE p.perm_code LIKE 'exam%' ORDER BY r.role_code, p.perm_code;
