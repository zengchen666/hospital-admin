-- ============================================================================
-- 17 公共卫生模块补丁：权限点 + 角色授权 + 疫苗初始数据
-- 依赖：06_public_health.sql（表）、08_seed.sql（角色）
-- 可重复执行（依赖 uk_perm_code / uk_role_perm）
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 新增权限点
--    说明：seed 里已有 health:record:edit(3042) / health:vaccine:add(3043) /
--    health:infectious:report(3044)，此处只补「查询」与体检相关权限，不重复定义。
--    ⚠️ sys_permission 上有 uk_perm_code 唯一键，同名权限会被吃掉，
--       新增前务必先 SELECT 确认名字没被用过。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `menu_id`, `perm_type`, `data_scope`, `status`)
VALUES
  (3064, 'health:query',          '公卫查询',     NULL, 2, 1, 1),
  (3065, 'health:checkup:add',    '体检登记',     NULL, 2, 1, 1),
  (3066, 'health:checkup:report', '体检报告出具', NULL, 2, 1, 1),
  (3067, 'health:package:edit',   '体检套餐维护', NULL, 2, 1, 1)
ON DUPLICATE KEY UPDATE
  `perm_name` = VALUES(`perm_name`);

-- ---------------------------------------------------------------------------
-- 2. 角色授权（按 perm_code 反查 permission_id，不硬编码权限 ID）
--    依据《需求文档》3.1 角色 × 模块矩阵：公卫体检列
--      系统管理员 ○ / 院长 ○ / 医生 ◐ / 护士 ◐ / 其余 —
--
--    ⚠️ sys_role_permission.id 没有自增，必须显式给值：
--       不给值时非严格模式下会被填成 0，第二条起全撞主键，
--       配合 INSERT IGNORE 就是「只有第一行插进去」。
-- ---------------------------------------------------------------------------

-- 清理上一版误插入的 id=0 记录（若存在）
DELETE FROM `sys_role_permission` WHERE `id` = 0;

SET @seq := (SELECT IFNULL(MAX(`id`), 9100) FROM `sys_role_permission`);

-- 查询类：管理员、院长、医生、护士
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('health:query') AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director', 'doctor', 'nurse')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 健康档案维护：医生、护士
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('health:record:edit') AND p.`deleted` = 0
 WHERE r.`role_code` IN ('doctor', 'nurse')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 疫苗接种登记：护士（接种是护士站的活）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('health:vaccine:add') AND p.`deleted` = 0
 WHERE r.`role_code` IN ('nurse')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 传染病上报：医生（诊断后依法上报）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('health:infectious:report') AND p.`deleted` = 0
 WHERE r.`role_code` IN ('doctor')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 体检登记 / 出报告：医生
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('health:checkup:add', 'health:checkup:report')
       AND p.`deleted` = 0
 WHERE r.`role_code` IN ('doctor')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- 体检套餐维护：管理员、院长（套餐定价属于管理配置，不给一线医生）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`)
SELECT (@seq := @seq + 1), r.`id`, p.`id`
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.`perm_code` IN ('health:package:edit') AND p.`deleted` = 0
 WHERE r.`role_code` IN ('admin', 'director')
ON DUPLICATE KEY UPDATE `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 3. 结构修正：biz_checkup 补「金额快照」列
--    套餐会调价，体检单只存 package_id 的话，一年前买的套餐会被按今天的价重算，
--    历史账就对不上。与处方明细存药品单价快照是同一个道理。
--    MySQL 不支持 ADD COLUMN IF NOT EXISTS，用预处理语句做到可重复执行。
-- ---------------------------------------------------------------------------
SET @amount_exists := (
    SELECT COUNT(1) FROM `information_schema`.`COLUMNS`
     WHERE `TABLE_SCHEMA` = 'hospital'
       AND `TABLE_NAME` = 'biz_checkup'
       AND `COLUMN_NAME` = 'amount');

SET @ddl := IF(@amount_exists = 0,
    'ALTER TABLE `biz_checkup` ADD COLUMN `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 '
        'COMMENT ''体检金额快照（以登记时的套餐价为准，不随套餐调价变动）'' AFTER `package_name`',
    'SELECT ''amount 列已存在，跳过'' AS `skip_reason`');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 4. 疫苗初始数据（疫苗复用药品库存模型：biz_drug.category='疫苗' + biz_stock）
--    固定 ID + INSERT IGNORE，可重复执行
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `biz_drug`
  (`id`, `drug_code`, `drug_name`, `generic_name`, `category`, `spec`, `unit`, `sale_price`, `status`)
VALUES
  (9107, 'YM001', '流感疫苗（四价）', '四价流感病毒裂解疫苗', '疫苗', '0.5ml/支', '支', 128.00, 1),
  (9108, 'YM002', '乙肝疫苗（重组）', '重组乙型肝炎疫苗',     '疫苗', '20ug/支',  '支',  86.00, 1);

-- 疫苗库存（库房 8002 门诊药房，批号用于接种记录追溯）
INSERT IGNORE INTO `biz_stock`
  (`id`, `drug_id`, `warehouse_id`, `batch_no`, `quantity`, `expire_date`)
VALUES
  (9207, 9107, 8002, 'FLU20261001', 200, '2027-03-31'),
  (9208, 9108, 8002, 'HBV20260915', 150, '2028-09-14');

-- ---------------------------------------------------------------------------
-- 5. 自检
-- ---------------------------------------------------------------------------
-- SELECT r.role_code, COUNT(*) AS perm_count
--   FROM sys_role r LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
--  GROUP BY r.role_code ORDER BY perm_count DESC;
-- SELECT perm_code FROM sys_permission WHERE perm_code LIKE 'health:%' ORDER BY id;
