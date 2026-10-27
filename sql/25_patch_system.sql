-- =============================================================================
-- 模块一 系统管理剩余：角色管理 / 菜单管理 启用
--
-- 菜单 202、203 此前是「已建未启用」（status=0），前端动态路由拉不到，
-- 表现为「侧边栏有系统管理，点开只有用户管理」。
--
-- 判权码同步调整：
--   202 角色管理  system:role:assign → system:role:query
--   203 菜单管理  system:menu:edit   → system:menu:query
-- 理由：这两个页一期只做<b>查看</b>。用「分配 / 编辑」去判一个只读页，
--       等于把「能看」的门槛抬到了「能改」，结果是所有人都看不到。
--
-- 新增权限的 id 先查过空位：3076 / 3077 / 3078（MAX(id)=3075）。
-- ⚠️ 血的教训：id 3064 已被 health:query 占用，曾用它插 report:config，
--    ON DUPLICATE KEY UPDATE 命中后把公卫权限名改掉了。
--    因此下面每条 INSERT 都加了 perm_code 一致性保护。
-- =============================================================================

USE `hospital`;

-- ---------------------------------------------------------------------------
-- 1. 新增「查看类」权限
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3076, 'system:role:query', '角色查看',   2, 202, 1, 1),
    (3077, 'system:menu:query', '菜单查看',   2, 203, 1, 1),
    (3078, 'system:log:query',  '日志查看',   2, 206, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = IF(`perm_code` = VALUES(`perm_code`), VALUES(`perm_name`), `perm_name`),
    `status`    = IF(`perm_code` = VALUES(`perm_code`), VALUES(`status`), `status`);

-- ---------------------------------------------------------------------------
-- 2. 菜单启用并改判权码
-- ---------------------------------------------------------------------------
UPDATE `sys_menu`
   SET `perms` = 'system:role:query', `status` = 1, `visible` = 1
 WHERE `id` = 202;

UPDATE `sys_menu`
   SET `perms` = 'system:menu:query', `status` = 1, `visible` = 1
 WHERE `id` = 203;

-- 206 操作日志：原判 security:log:export（导出），一期只做查看，
-- 用导出权限去判一个只读页会导致「能看日志的人」被抬到「能导出日志」的门槛。
UPDATE `sys_menu`
   SET `perms` = 'system:log:query', `status` = 1, `visible` = 1
 WHERE `id` = 206;

-- ---------------------------------------------------------------------------
-- 2.2 数据字典 / 系统参数：补「查看」权限并启用菜单
--
-- 与角色、菜单同一条原则：<b>看用什么码，就判什么码</b>。
-- 204/205 原本判的是 dict:edit / config:edit，只读用户被挡在门外，
-- 结果就是「只有能改的人才能看」，而能改的人全院只有两个。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
    (`id`, `perm_code`, `perm_name`, `perm_type`, `menu_id`, `data_scope`, `status`)
VALUES
    (3079, 'system:dict:query',   '字典查看', 2, 204, 1, 1),
    (3080, 'system:config:query', '参数查看', 2, 205, 1, 1)
ON DUPLICATE KEY UPDATE
    `perm_name` = IF(`perm_code` = VALUES(`perm_code`), VALUES(`perm_name`), `perm_name`),
    `status`    = IF(`perm_code` = VALUES(`perm_code`), VALUES(`status`), `status`);

UPDATE `sys_menu`
   SET `perms` = 'system:dict:query', `status` = 1, `visible` = 1
 WHERE `id` = 204;

UPDATE `sys_menu`
   SET `perms` = 'system:config:query', `status` = 1, `visible` = 1
 WHERE `id` = 205;

-- ---------------------------------------------------------------------------
-- 3. 授权：管理员看全部；院长可看角色与菜单（但不含用户增删改）
-- ---------------------------------------------------------------------------
SET @seq = (SELECT IFNULL(MAX(id), 9500) FROM `sys_role_permission`);

INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p
    ON p.`perm_code` IN ('system:role:query', 'system:menu:query', 'system:log:query',
                         'system:dict:query', 'system:config:query')
   AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director') AND r.`deleted` = 0;

-- 3.2 字典与参数的编辑权：仅 admin（改错一个取值会让全院某个下拉框同时失真）
INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p
    ON p.`perm_code` IN ('system:dict:edit', 'system:config:edit')
   AND p.`deleted` = 0
 WHERE r.`role_code` = 'admin' AND r.`deleted` = 0;

-- 3.3 审计日志导出权：admin + 院长
INSERT IGNORE INTO `sys_role_permission`
    (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `deleted`, `version`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`, NOW(), NOW(), 0, 0
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` = 'security:log:export' AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director') AND r.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 4. 字典种子数据
--
-- 这几项是业务代码里已经硬编码过的枚举（性别、支付方式、婚姻状况…），
-- 挪到字典里是为了让「下拉框选项」和「库里存的值」有同一个出处。
-- ⚠️ 这里只补数据，不回去改业务代码引用枚举的地方 ——
--    那是下一轮的事，先把字典的增删改查跑通。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict`
    (`id`, `dict_type`, `dict_name`, `remark`, `status`, `create_time`, `update_time`, `deleted`, `version`)
VALUES
    (6001, 'gender',         '性别',     '与 biz_patient.gender 一致',       1, NOW(), NOW(), 0, 0),
    (6002, 'pay_type',       '支付方式', '与 biz_charge.pay_type 一致',      1, NOW(), NOW(), 0, 0),
    (6003, 'marital_status', '婚姻状况', '健康档案用',                       1, NOW(), NOW(), 0, 0),
    (6004, 'blood_type',     '血型',     '健康档案用',                       1, NOW(), NOW(), 0, 0),
    (6005, 'id_card_type',   '证件类型', '患者建档用',                       1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO `sys_dict_item`
    (`id`, `dict_type`, `item_label`, `item_value`, `sort`, `css_class`, `status`,
     `create_time`, `update_time`, `deleted`, `version`)
VALUES
    (6101, 'gender', '男',   '1', 1, NULL, 1, NOW(), NOW(), 0, 0),
    (6102, 'gender', '女',   '2', 2, NULL, 1, NOW(), NOW(), 0, 0),
    (6103, 'gender', '未知', '0', 3, 'info', 1, NOW(), NOW(), 0, 0),
    (6111, 'pay_type', '现金',   '1', 1, NULL, 1, NOW(), NOW(), 0, 0),
    (6112, 'pay_type', '医保',   '2', 2, NULL, 1, NOW(), NOW(), 0, 0),
    (6113, 'pay_type', '微信',   '3', 3, NULL, 1, NOW(), NOW(), 0, 0),
    (6114, 'pay_type', '支付宝', '4', 4, NULL, 1, NOW(), NOW(), 0, 0),
    (6115, 'pay_type', '银行卡', '5', 5, NULL, 1, NOW(), NOW(), 0, 0),
    (6121, 'marital_status', '未婚', '0', 1, NULL, 1, NOW(), NOW(), 0, 0),
    (6122, 'marital_status', '已婚', '1', 2, NULL, 1, NOW(), NOW(), 0, 0),
    (6123, 'marital_status', '离异', '2', 3, NULL, 1, NOW(), NOW(), 0, 0),
    (6124, 'marital_status', '丧偶', '3', 4, NULL, 1, NOW(), NOW(), 0, 0),
    (6131, 'blood_type', 'A型',  'A',  1, NULL, 1, NOW(), NOW(), 0, 0),
    (6132, 'blood_type', 'B型',  'B',  2, NULL, 1, NOW(), NOW(), 0, 0),
    (6133, 'blood_type', 'AB型', 'AB', 3, NULL, 1, NOW(), NOW(), 0, 0),
    (6134, 'blood_type', 'O型',  'O',  4, NULL, 1, NOW(), NOW(), 0, 0),
    (6135, 'blood_type', '不详', 'U',  5, 'info', 1, NOW(), NOW(), 0, 0),
    (6141, 'id_card_type', '身份证',   '1', 1, NULL, 1, NOW(), NOW(), 0, 0),
    (6142, 'id_card_type', '护照',     '2', 2, NULL, 1, NOW(), NOW(), 0, 0),
    (6143, 'id_card_type', '军官证',   '3', 3, NULL, 1, NOW(), NOW(), 0, 0),
    (6144, 'id_card_type', '港澳通行证', '4', 4, NULL, 1, NOW(), NOW(), 0, 0);

-- ---------------------------------------------------------------------------
-- 5. 系统参数种子
--
-- config_type=1 的几条是「安全配置」页要用的：密码长度、登录锁定、IP 白名单。
-- 它们与业务参数放在同一张表里 —— 两张参数表会让「改一个安全策略」
-- 变成「先想清楚该去哪个页面改」。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_config`
    (`id`, `config_key`, `config_value`, `config_name`, `config_type`, `remark`,
     `create_time`, `update_time`, `deleted`, `version`)
VALUES
    (7001, 'security.password_min_length', '8',      '密码最小长度',   1, '新建与重置密码时校验', NOW(), NOW(), 0, 0),
    (7002, 'security.login_max_attempts',  '5',      '登录失败上限',   1, '超过后锁定账号',       NOW(), NOW(), 0, 0),
    (7003, 'security.login_lock_minutes',  '15',     '锁定时长（分钟）', 1, '登录失败超限后锁定多久', NOW(), NOW(), 0, 0),
    (7004, 'security.ip_whitelist',        '',       'IP 白名单',      1, '逗号分隔，留空表示不限制', NOW(), NOW(), 0, 0),
    (7005, 'security.session_timeout_hours', '2',    '会话超时（小时）', 1, 'access token 有效期', NOW(), NOW(), 0, 0),
    (7006, 'appointment.advance_days',     '7',      '预约提前天数',   2, '最多可提前多少天预约', NOW(), NOW(), 0, 0),
    (7007, 'work.start_time',              '08:00',  '上班时间',       2, '号源生成的起始时间',   NOW(), NOW(), 0, 0),
    (7008, 'work.end_time',                '17:30',  '下班时间',       2, '号源生成的结束时间',   NOW(), NOW(), 0, 0);

-- ---------------------------------------------------------------------------
-- 6. 数据安全：启用备份与安全配置菜单
--
-- 1301 判 security:backup:run（执行备份）—— 备份历史里含库名、路径、大小，
--      本身就该被保护，因此「看历史」与「执行备份」共用同一个码。
-- 1302 判 security:config:edit（安全配置）—— 只给 admin。
--      页面读的是系统参数接口（system:config:query），admin 两者都有；
--      院长只有 system:config:query，因此看不到这个入口 —— 这是刻意的结果。
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` SET `status` = 1, `visible` = 1 WHERE `id` IN (1301, 1302);

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
-- SELECT id, menu_name, perms, status FROM sys_menu WHERE id BETWEEN 201 AND 206;
-- SELECT r.role_code, p.perm_code FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
--   JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
--  WHERE p.perm_code IN ('system:role:query','system:menu:query','system:log:query')
--  ORDER BY r.role_code, p.perm_code;
