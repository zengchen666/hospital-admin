-- ============================================================================
-- 19 菜单判权码全量对齐
--
-- 背景：sys_menu.perms 里大量权限码在 sys_permission 中根本不存在
--       （如 patient:list 实际是 patient:query、clinic:visit:list 实际是
--        clinic:query）。SysMenuService 按「用户权限是否包含 menu.perms」
--        过滤菜单，于是除顶层目录（perms 为 NULL）外，几乎所有二级菜单
--        对所有人不可见 —— 侧边栏只剩一排空的目录。
--
-- 处理：逐条对齐到 sys_permission 中真实存在的权限码；
--       尚未实现页面的菜单先置 status=0，等页面做完再逐个打开，
--       避免侧边栏出现点进去是空白的死链。
--
-- 可重复执行
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

UPDATE `sys_menu` m
  JOIN (
    -- 模块一 系统管理
    SELECT 201 id, 'system:user:query'   p, 1 s UNION ALL  -- 用户管理（已实现）
    SELECT 202,    'system:role:assign',    0 UNION ALL
    SELECT 203,    'system:menu:edit',      0 UNION ALL
    SELECT 204,    'system:dict:edit',      0 UNION ALL
    SELECT 205,    'system:config:edit',    0 UNION ALL
    SELECT 206,    'security:log:export',   0 UNION ALL
    -- 模块二 患者管理
    SELECT 301,    'patient:query',         1 UNION ALL  -- 患者档案（已实现）
    SELECT 302,    'patient:card:issue',    0 UNION ALL
    SELECT 303,    'patient:followup:add',  0 UNION ALL
    -- 模块三 挂号与预约
    SELECT 401,    'registration:add',              0 UNION ALL
    SELECT 402,    'registration:schedule:edit',    0 UNION ALL
    SELECT 403,    'registration:call',             0 UNION ALL
    -- 模块四 门诊管理（页面已实现 → 启用）
    SELECT 501,    'clinic:query',          1 UNION ALL
    SELECT 502,    'clinic:query',          1 UNION ALL
    -- 模块五 住院管理（页面已实现 → 启用）
    --   护理记录菜单判 inpatient:nursing:add 而不是 inpatient:query：
    --   只让他看不让记的护士没有意义，能看到就一定能写
    SELECT 601,    'inpatient:query',       1 UNION ALL
    SELECT 602,    'inpatient:query',       1 UNION ALL
    SELECT 603,    'inpatient:query',       1 UNION ALL
    SELECT 604,    'inpatient:nursing:add', 1 UNION ALL
    -- 模块六 药品与药房管理（页面已实现 → 启用）
    --   菜单一律判 pharmacy:query，页内按钮再各自判权。
    --   采购管理曾判 pharmacy:stock:in，结果有审核权的院长看不到菜单 ——
    --   权限给了、入口没有，等于功能不存在
    SELECT 701,    'pharmacy:query',        1 UNION ALL
    SELECT 702,    'pharmacy:query',        1 UNION ALL
    SELECT 703,    'pharmacy:query',        1 UNION ALL
    SELECT 704,    'pharmacy:query',        1 UNION ALL
    -- 模块七 检查检验
    SELECT 801,    'exam:query',            0 UNION ALL
    SELECT 802,    'exam:query',            0 UNION ALL
    SELECT 803,    'exam:query',            0 UNION ALL
    -- 模块八 收费与财务
    SELECT 901,    'finance:query',         0 UNION ALL
    SELECT 902,    'finance:query',         0 UNION ALL
    SELECT 903,    'finance:invoice:void',  0 UNION ALL
    SELECT 904,    'finance:daily:submit',  0 UNION ALL
    SELECT 905,    'finance:treat:edit',    0 UNION ALL
    -- 模块九 公共卫生
    SELECT 1001,   'health:query',          0 UNION ALL
    SELECT 1002,   'health:query',          0 UNION ALL
    SELECT 1003,   'health:query',          0 UNION ALL
    SELECT 1004,   'health:query',          0 UNION ALL
    -- 模块十 统计报表
    --   用 report:query 而不是 report:view：view 只给了 admin，
    --   而报表本该是院长、医生、财务都要看的
    SELECT 1101,   'report:query',          0 UNION ALL
    SELECT 1102,   'report:query',          0 UNION ALL
    SELECT 1103,   'report:query',          0 UNION ALL
    SELECT 1104,   'report:query',          0 UNION ALL
    -- 模块十一 消息与通知（已实现，保持启用）
    SELECT 1201,   'message:notice:edit',   1 UNION ALL
    SELECT 1202,   'message:query',         1 UNION ALL
    SELECT 1203,   'message:sms:send',      1 UNION ALL
    SELECT 1204,   'message:warning:scan',  1 UNION ALL
    -- 模块十二 数据安全
    SELECT 1301,   'security:backup:run',   0 UNION ALL
    SELECT 1302,   'security:config:edit',  0
  ) x ON x.id = m.id
   SET m.`perms` = x.p,
       m.`status` = x.s
 WHERE m.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 自检：菜单 perms 必须在 sys_permission 里真实存在，否则该菜单永远不可见
-- ---------------------------------------------------------------------------
-- SELECT m.id, m.menu_name, m.perms,
--        IF(p.id IS NULL, '❌权限点不存在', 'OK') AS chk
--   FROM sys_menu m
--   LEFT JOIN sys_permission p ON p.perm_code = m.perms AND p.deleted = 0
--  WHERE m.deleted = 0 AND m.menu_type = 2
--  ORDER BY m.id;
