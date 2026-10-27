-- =============================================================================
-- 权限铺开：给 6 个业务角色分配权限（此前除 admin 外全是 0 权限）
--
-- 依据《需求文档》3.1 的角色 × 模块矩阵：
--   ● 全权（增删改查+配置）  ◐ 只读+本人业务操作  ○ 仅只读  — 无权限
--
-- 写法说明：按 perm_code 反查 permission_id 再插入，不硬编码权限 ID ——
--   权限 ID 会随补丁增加而变化，写死 ID 的脚本在别人加过权限后就错了。
--   sys_role_permission 有 uk_role_perm(role_id, permission_id)，可重复执行。
-- =============================================================================

USE `hospital`;

-- ----------------------------------------------------------------------------
-- 0. 修正 14 号脚本遗留的悬空授权（permission_id=3062 并不存在）
-- ----------------------------------------------------------------------------
DELETE FROM `sys_role_permission` WHERE `permission_id` = 3062;

-- seed 里的 report:view 与新增的 report:query 语义重复，停用旧码，
-- 避免同一件事有两把钥匙（以后授权时不知道该给哪个）
UPDATE `sys_permission` SET `status` = 0 WHERE `perm_code` = 'report:view' AND `deleted` = 0;

SET @seq := 9100;

-- ----------------------------------------------------------------------------
-- 1. 院长 / 管理层（2002）：全模块只读 + 报表全权
--    管理层要看全院经营数据，但不该直接下场改业务单据
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2002, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` IN (
     'system:user:query',
     'patient:query',
     'registration:query',
     'clinic:query',
     'inpatient:query',
     'pharmacy:query',
     'exam:query',
     'finance:query',
     'report:query', 'report:export', 'report:generate',
     'data:all'
   )
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 2. 医生（2003）：门诊/住院全权，患者与检查「只读 + 本人业务操作」
--    不给处方审核之外的财务权限，也不给系统管理
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2003, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` IN (
     'patient:query', 'patient:add', 'patient:edit', 'patient:followup:add',
     'registration:query', 'registration:call',
     'clinic:query', 'clinic:receive', 'clinic:prescribe', 'clinic:presc:audit',
     'inpatient:query', 'inpatient:admission:add', 'inpatient:order:add', 'inpatient:nursing:add',
     'pharmacy:query',
     'exam:query', 'exam:apply:add', 'exam:critical:handle',
     'finance:query',
     'report:query',
     'data:dept'
   )
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 3. 护士（2004）：住院全权（执行医嘱、护理记录），门诊只到接诊分诊
--    明确不给收费、不给报表 —— 护士站不该看到全院收入
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2004, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` IN (
     'patient:query', 'patient:followup:add',
     'registration:query', 'registration:call',
     'clinic:query', 'clinic:receive',
     'inpatient:query', 'inpatient:order:exec', 'inpatient:nursing:add',
     'pharmacy:query',
     'exam:query',
     'data:dept'
   )
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 4. 挂号 / 收费员（2005）：挂号全权 + 收费全权，数据范围限定本人经手
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2005, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` IN (
     'patient:query', 'patient:add', 'patient:edit', 'patient:card:issue',
     'registration:query', 'registration:add', 'registration:cancel',
     'registration:call', 'registration:schedule:edit',
     'finance:query', 'finance:charge:add', 'finance:refund:apply',
     'data:self'
   )
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 5. 药房 / 库房员（2006）：只给药房模块，且只有它能动库存
--    库存是所有消耗类数据的源头，能改库存的角色越少越好
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2006, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` IN (
     'pharmacy:query', 'pharmacy:drug:edit', 'pharmacy:stock:in',
     'pharmacy:stock:out', 'pharmacy:dispense', 'pharmacy:return',
     'data:dept'
   )
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 6. 财务人员（2007）：收费模块全权（含退费审核、日结、调价）+ 报表只读导出
--    不给 finance:charge:add —— 收钱是收费员的活，财务负责审和结，职责要分开
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2007, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` IN (
     'finance:query', 'finance:refund:apply', 'finance:refund:audit',
     'finance:daily:submit', 'finance:treat:edit',
     'report:query', 'report:export',
     'data:dept'
   )
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 7. 超级管理员补齐全部数据范围
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), 2001, p.id
  FROM `sys_permission` p
 WHERE p.`deleted` = 0
   AND p.`perm_code` = 'data:all'
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ----------------------------------------------------------------------------
-- 校验输出
-- ----------------------------------------------------------------------------
SELECT r.`role_code`, r.`role_name`, COUNT(rp.`permission_id`) AS `perm_count`
  FROM `sys_role` r
  LEFT JOIN `sys_role_permission` rp ON rp.`role_id` = r.`id` AND rp.`deleted` = 0
 WHERE r.`deleted` = 0
 GROUP BY r.`id`, r.`role_code`, r.`role_name`
 ORDER BY r.`id`;
