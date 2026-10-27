-- ============================================================================
-- 08 初始数据（开发/演示用）
-- 全部使用 INSERT IGNORE + 固定 ID，可重复执行不报错、不覆盖已有数据
-- ============================================================================
-- ⚠️ 密码说明（必读）
--   sys_user.password 统一写入 "{noop}123456"（明文标识 + 123456）。
--   这是 Spring Security DelegatingPasswordEncoder 的约定写法，后端须配置：
--       @Bean PasswordEncoder passwordEncoder() {
--           return PasswordEncoderFactories.createDelegatingPasswordEncoder();
--       }
--   若项目使用 BCryptPasswordEncoder，请先生成哈希再替换，例如执行一次：
--       new BCryptPasswordEncoder().encode("123456")
--       → 得到 $2a$10$xxxx...（60 字符），把 "{noop}123456" 整段替换成该值。
--   **生产环境必须替换，禁止使用本文件的初始密码。**
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 科室
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dept` (`id`,`parent_id`,`dept_name`,`dept_code`,`dept_type`,`sort`,`status`) VALUES
(1000, 0,    '示例医院',   'ROOT',      1, 0, 1),
(1001, 1000, '内科',       'DEPT_NK',   1, 1, 1),
(1002, 1000, '外科',       'DEPT_WK',   1, 2, 1),
(1003, 1000, '儿科',       'DEPT_EK',   1, 3, 1),
(1004, 1000, '检验科',     'DEPT_JYK',  3, 4, 1),
(1005, 1000, '药房',       'DEPT_YF',   3, 5, 1),
(1006, 1000, '财务科',     'DEPT_CWK',  3, 6, 1);

-- ---------------------------------------------------------------------------
-- 2. 角色（对应需求文档 3.1 的 7 类角色）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role` (`id`,`role_name`,`role_code`,`remark`,`status`) VALUES
(2001, '超级管理员', 'admin',   '系统最高权限，不受数据权限限制', 1),
(2002, '院长/管理层','director','只读全局 + 统计报表权限',        1),
(2003, '医生',       'doctor',  '门诊/住院业务操作权限',          1),
(2004, '护士',       'nurse',   '住院护理与医嘱执行权限',         1),
(2005, '挂号收费员', 'cashier', '挂号、收费、退费权限',           1),
(2006, '药房库房员', 'pharm',   '药品、库存、发药权限',           1),
(2007, '财务人员',   'finance', '收费财务与日结月结权限',         1);

-- ---------------------------------------------------------------------------
-- 3. 菜单（目录=1 菜单=2）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`perms`,`icon`,`sort`,`visible`,`status`) VALUES
(100, 0,    '首页',        2, '/dashboard',        'dashboard/index',        NULL,                     'home',      1, 1, 1),
(200, 0,    '系统管理',    1, '/system',           NULL,                     NULL,                     'setting',   2, 1, 1),
(201, 200,  '用户管理',    2, '/system/user',      'system/user/index',      'system:user:list',       'user',      1, 1, 1),
(202, 200,  '角色管理',    2, '/system/role',      'system/role/index',      'system:role:list',       'peoples',   2, 1, 1),
(203, 200,  '菜单管理',    2, '/system/menu',      'system/menu/index',      'system:menu:list',       'tree',      3, 1, 1),
(204, 200,  '数据字典',    2, '/system/dict',      'system/dict/index',      'system:dict:list',       'dict',      4, 1, 1),
(205, 200,  '系统参数',    2, '/system/config',    'system/config/index',    'system:config:list',     'edit',      5, 1, 1),
(206, 200,  '操作日志',    2, '/system/oplog',     'system/oplog/index',     'system:oplog:list',      'form',      6, 1, 1),
(300, 0,    '患者管理',    1, '/patient',          NULL,                     NULL,                     'peoples',   3, 1, 1),
(301, 300,  '患者档案',    2, '/patient/list',     'patient/list/index',     'patient:list',           'user',      1, 1, 1),
(302, 300,  '就诊卡管理',  2, '/patient/card',     'patient/card/index',     'patient:card:list',      'card',      2, 1, 1),
(303, 300,  '随访管理',    2, '/patient/followup', 'patient/follow/index',   'patient:followup:list',  'phone',     3, 1, 1),
(400, 0,    '挂号预约',    1, '/registration',     NULL,                     NULL,                     'calendar',  4, 1, 1),
(401, 400,  '现场挂号',    2, '/registration/add', 'registration/add/index', 'registration:add',       'edit',      1, 1, 1),
(402, 400,  '排班管理',    2, '/registration/sched','registration/sched/index','registration:schedule:list','time',  2, 1, 1),
(403, 400,  '候诊队列',    2, '/registration/queue','registration/queue/index','registration:queue',    'list',      3, 1, 1),
(500, 0,    '门诊管理',    1, '/clinic',           NULL,                     NULL,                     'documentation', 5, 1, 1),
(501, 500,  '医生工作台',  2, '/clinic/workbench', 'clinic/workbench/index', 'clinic:visit:list',      'component', 1, 1, 1),
(502, 500,  '处方管理',    2, '/clinic/presc',     'clinic/presc/index',     'clinic:presc:list',      'form',      2, 1, 1),
(600, 0,    '住院管理',    1, '/inpatient',        NULL,                     NULL,                     'international', 6, 1, 1),
(601, 600,  '床位一览',    2, '/inpatient/bed',    'inpatient/bed/index',    'inpatient:bed:list',     'grid',      1, 1, 1),
(602, 600,  '入院登记',    2, '/inpatient/admission','inpatient/admission/index','inpatient:admission:list','edit', 2, 1, 1),
(603, 600,  '医嘱管理',    2, '/inpatient/order',  'inpatient/order/index',  'inpatient:order:list',   'list',      3, 1, 1),
(604, 600,  '护理记录',    2, '/inpatient/nursing','inpatient/nursing/index','inpatient:nursing:list', 'edit',      4, 1, 1),
(700, 0,    '药房管理',    1, '/pharmacy',         NULL,                     NULL,                     'shopping',  7, 1, 1),
(701, 700,  '药品目录',    2, '/pharmacy/drug',    'pharmacy/drug/index',    'pharmacy:drug:list',     'list',      1, 1, 1),
(702, 700,  '库存管理',    2, '/pharmacy/stock',   'pharmacy/stock/index',   'pharmacy:stock:list',    'chart',     2, 1, 1),
(703, 700,  '采购管理',    2, '/pharmacy/purchase','pharmacy/purchase/index','pharmacy:purchase:list', 'shopping',  3, 1, 1),
(704, 700,  '门诊发药',    2, '/pharmacy/dispense','pharmacy/dispense/index','pharmacy:dispense:list', 'export',    4, 1, 1),
(800, 0,    '检查检验',    1, '/exam',             NULL,                     NULL,                     'search',    8, 1, 1),
(801, 800,  '申请管理',    2, '/exam/apply',       'exam/apply/index',       'exam:apply:list',        'edit',      1, 1, 1),
(802, 800,  '检验工作台',  2, '/exam/workbench',   'exam/workbench/index',   'exam:report:list',       'component', 2, 1, 1),
(803, 800,  '报告查询',    2, '/exam/report',      'exam/report/index',      'exam:report:query',      'documentation', 3, 1, 1),
(900, 0,    '收费财务',    1, '/finance',          NULL,                     NULL,                     'money',     9, 1, 1),
(901, 900,  '门诊收费',    2, '/finance/charge',   'finance/charge/index',   'finance:charge:list',    'money',     1, 1, 1),
(902, 900,  '退费管理',    2, '/finance/refund',   'finance/refund/index',   'finance:refund:list',    'undo',      2, 1, 1),
(903, 900,  '发票管理',    2, '/finance/invoice',  'finance/invoice/index',  'finance:invoice:list',   'form',      3, 1, 1),
(904, 900,  '日结月结',    2, '/finance/settle',   'finance/settle/index',   'finance:daily:list',     'chart',     4, 1, 1),
(905, 900,  '诊疗项目价格',2, '/finance/treat',    'finance/treat/index',    'finance:treat:list',     'list',      5, 1, 1),
(1000, 0,   '公共卫生',    1, '/health',           NULL,                     NULL,                     'tree-table', 10, 1, 1),
(1001, 1000,'健康档案',    2, '/health/record',    'health/record/index',    'health:record:list',     'documentation', 1, 1, 1),
(1002, 1000,'疫苗接种',    2, '/health/vaccine',   'health/vaccine/index',   'health:vaccine:list',    'edit',      2, 1, 1),
(1003, 1000,'传染病上报',  2, '/health/infectious','health/infectious/index','health:infectious:list', 'warning',   3, 1, 1),
(1004, 1000,'体检管理',    2, '/health/checkup',   'health/checkup/index',   'health:checkup:list',    'list',      4, 1, 1),
(1100, 0,   '统计报表',    1, '/report',           NULL,                     NULL,                     'chart',     11, 1, 1),
(1101, 1100,'门诊统计',    2, '/report/outpatient','report/outpatient/index','report:outpatient',      'chart',     1, 1, 1),
(1102, 1100,'住院统计',    2, '/report/inpatient', 'report/inpatient/index', 'report:inpatient',       'chart',     2, 1, 1),
(1103, 1100,'收入统计',    2, '/report/income',    'report/income/index',    'report:income',          'money',     3, 1, 1),
(1104, 1100,'药品统计',    2, '/report/drug',      'report/drug/index',      'report:drug',            'shopping',  4, 1, 1),
(1200, 0,   '消息中心',    2, '/message',          'message/index',          'message:list',           'message',   12, 1, 1),
(1201, 1200,'公告管理',    2, '/message/notice',   'message/notice/index',   'message:notice:list',    'education', 1, 1, 1),
(1300, 0,   '数据安全',    1, '/security',         NULL,                     NULL,                     'lock',      13, 1, 1),
(1301, 1300,'数据备份',    2, '/security/backup',  'security/backup/index',  'security:backup:list',   'download',  1, 1, 1),
(1302, 1300,'安全配置',    2, '/security/config',  'security/config/index',  'security:config:edit',   'setting',   2, 1, 1);

-- ---------------------------------------------------------------------------
-- 4. 权限点（按钮 / 数据级）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (`id`,`perm_code`,`perm_name`,`perm_type`,`data_scope`,`status`) VALUES
(3001,'system:user:query',    '用户查询',    2,1,1),
(3002,'system:user:add',      '用户新增',    2,1,1),
(3003,'system:user:edit',     '用户修改',    2,1,1),
(3004,'system:user:delete',   '用户删除',    2,1,1),
(3005,'system:user:reset',    '重置密码',    2,1,1),
(3006,'system:role:assign',   '分配权限',    2,1,1),
(3007,'system:menu:edit',     '菜单编辑',    2,1,1),
(3008,'system:dict:edit',     '字典编辑',    2,1,1),
(3009,'system:config:edit',   '参数编辑',    2,1,1),
(3010,'patient:add',          '患者建档',    2,1,1),
(3011,'patient:edit',         '患者修改',    2,1,1),
(3012,'patient:delete',       '患者删除',    2,1,1),
(3013,'patient:decrypt',      '患者信息解密',3,1,1),
(3014,'patient:card:issue',   '发卡/补卡',   2,1,1),
(3015,'patient:followup:add', '新增随访',    2,1,1),
(3016,'registration:add',     '挂号办理',    2,1,1),
(3017,'registration:cancel',  '退号',        2,1,1),
(3018,'registration:call',    '叫号',        2,1,1),
(3019,'registration:schedule:edit','排班维护',2,1,1),
(3020,'clinic:receive',       '接诊',        2,1,1),
(3021,'clinic:prescribe',     '开处方',      2,1,1),
(3022,'clinic:presc:audit',   '处方审核',    2,1,1),
(3023,'inpatient:admission:add','入院登记',  2,1,1),
(3024,'inpatient:order:add',  '开医嘱',      2,1,1),
(3025,'inpatient:order:exec', '执行医嘱',    2,1,1),
(3026,'inpatient:nursing:add','护理记录',    2,1,1),
(3027,'pharmacy:drug:edit',   '药品维护',    2,1,1),
(3028,'pharmacy:stock:in',    '入库',        2,1,1),
(3029,'pharmacy:stock:out',   '出库',        2,1,1),
(3030,'pharmacy:dispense',    '发药',        2,1,1),
(3031,'pharmacy:return',      '退药',        2,1,1),
(3032,'exam:apply:add',       '开检查单',    2,1,1),
(3033,'exam:report:entry',    '录入结果',    2,1,1),
(3034,'exam:report:audit',    '报告审核',    2,1,1),
(3035,'exam:critical:handle', '危急值处理',  2,1,1),
(3036,'finance:charge:add',   '收费',        2,1,1),
(3037,'finance:refund:apply', '退费申请',    2,1,1),
(3038,'finance:refund:audit', '退费审核',    2,1,1),
(3039,'finance:invoice:void', '发票作废',    2,1,1),
(3040,'finance:daily:submit', '提交日结',    2,1,1),
(3041,'finance:treat:edit',   '诊疗项目调价',2,1,1),
(3042,'health:record:edit',   '健康档案维护',2,1,1),
(3043,'health:vaccine:add',   '接种登记',    2,1,1),
(3044,'health:infectious:report','传染病上报',2,1,1),
(3045,'report:view',          '查看报表',    2,1,1),
(3046,'report:export',        '导出报表',    2,1,1),
(3047,'message:notice:publish','发布公告',   2,1,1),
(3048,'security:backup:run',  '执行备份',    2,1,1),
(3049,'security:backup:restore','数据恢复',  2,1,1),
(3050,'security:log:export',  '日志导出',    2,1,1),
(3051,'data:all',             '全部数据范围',4,1,1),
(3052,'data:dept',            '本科室数据',  4,2,1),
(3053,'data:self',            '本人数据',    4,3,1);

-- ---------------------------------------------------------------------------
-- 5. 用户（初始密码统一 123456，见文件头说明）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_user` (`id`,`username`,`password`,`real_name`,`gender`,`dept_id`,`role_id`,`status`) VALUES
(1, 'admin',     '{noop}123456', '系统管理员', 1, 1001, 2001, 1),
(2, 'director',  '{noop}123456', '周院长',     1, 1001, 2002, 1),
(3, 'doc01',     '{noop}123456', '张医生',     1, 1001, 2003, 1),
(4, 'nurse01',   '{noop}123456', '李护士',     0, 1001, 2004, 1),
(5, 'cashier01', '{noop}123456', '王收费',     0, 1006, 2005, 1),
(6, 'pharm01',   '{noop}123456', '赵药师',     1, 1005, 2006, 1),
(7, 'fin01',     '{noop}123456', '孙会计',     0, 1006, 2007, 1);

INSERT IGNORE INTO `sys_user_role` (`id`,`user_id`,`role_id`) VALUES
(5001,1,2001),(5002,2,2002),(5003,3,2003),(5004,4,2004),
(5005,5,2005),(5006,6,2006),(5007,7,2007);

-- 超级管理员授予全部权限
INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`)
SELECT 6000 + p.`id`, 2001, p.`id` FROM `sys_permission` p WHERE p.`deleted` = 0;

-- ---------------------------------------------------------------------------
-- 6. 数据字典
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict` (`id`,`dict_type`,`dict_name`,`remark`,`status`) VALUES
(4001,'gender',        '性别',        '全局通用', 1),
(4002,'dept_type',     '科室类型',    NULL,       1),
(4003,'reg_type',      '号别',        '挂号类型', 1),
(4004,'pay_type',      '支付方式',    NULL,       1),
(4005,'presc_type',    '处方类型',    NULL,       1),
(4006,'visit_type',    '就诊类型',    NULL,       1),
(4007,'bed_type',      '床位类型',    NULL,       1),
(4008,'order_type',    '医嘱类型',    NULL,       1),
(4009,'exam_type',     '检查检验类型',NULL,       1),
(4010,'blood_type',    '血型',        NULL,       1),
(4011,'follow_type',   '随访方式',    NULL,       1),
(4012,'msg_type',      '消息类型',    NULL,       1),
(4013,'abnormal_flag', '检验异常标志',NULL,       1),
(4014,'insurance_type','医保类别',    NULL,       1);

INSERT IGNORE INTO `sys_dict_item` (`id`,`dict_type`,`item_label`,`item_value`,`sort`,`css_class`,`status`) VALUES
(4101,'gender','女','0',1,NULL,1),
(4102,'gender','男','1',2,NULL,1),
(4103,'gender','未知','2',3,NULL,1),
(4111,'dept_type','门诊','1',1,NULL,1),
(4112,'dept_type','住院','2',2,NULL,1),
(4113,'dept_type','医技','3',3,NULL,1),
(4121,'reg_type','普通','1',1,NULL,1),
(4122,'reg_type','专家','2',2,'text-warning',1),
(4123,'reg_type','急诊','3',3,'text-danger',1),
(4131,'pay_type','现金','1',1,NULL,1),
(4132,'pay_type','微信','2',2,NULL,1),
(4133,'pay_type','支付宝','3',3,NULL,1),
(4134,'pay_type','医保','4',4,NULL,1),
(4135,'pay_type','银行卡','5',5,NULL,1),
(4141,'presc_type','西药','1',1,NULL,1),
(4142,'presc_type','中药','2',2,NULL,1),
(4143,'presc_type','中成药','3',3,NULL,1),
(4151,'visit_type','门诊','1',1,NULL,1),
(4152,'visit_type','住院','2',2,NULL,1),
(4161,'bed_type','普通','1',1,NULL,1),
(4162,'bed_type','重症','2',2,'text-danger',1),
(4163,'bed_type','VIP','3',3,'text-warning',1),
(4171,'order_type','长期','1',1,NULL,1),
(4172,'order_type','临时','2',2,NULL,1),
(4181,'exam_type','检验','1',1,NULL,1),
(4182,'exam_type','检查(影像)','2',2,NULL,1),
(4191,'blood_type','A','A',1,NULL,1),
(4192,'blood_type','B','B',2,NULL,1),
(4193,'blood_type','AB','AB',3,NULL,1),
(4194,'blood_type','O','O',4,NULL,1),
(4195,'blood_type','未知','U',5,NULL,1),
(4201,'follow_type','电话','1',1,NULL,1),
(4202,'follow_type','上门','2',2,NULL,1),
(4203,'follow_type','短信','3',3,NULL,1),
(4211,'msg_type','系统','1',1,NULL,1),
(4212,'msg_type','待办','2',2,NULL,1),
(4213,'msg_type','预警','3',3,'text-danger',1),
(4221,'abnormal_flag','正常','0',1,NULL,1),
(4222,'abnormal_flag','偏高','1',2,'text-danger',1),
(4223,'abnormal_flag','偏低','2',3,'text-info',1),
(4224,'abnormal_flag','定性异常','3',4,'text-warning',1),
(4231,'insurance_type','甲类','1',1,NULL,1),
(4232,'insurance_type','乙类','2',2,NULL,1),
(4233,'insurance_type','丙类(自费)','3',3,NULL,1);

-- ---------------------------------------------------------------------------
-- 7. 系统参数
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_config` (`id`,`config_key`,`config_value`,`config_name`,`config_type`,`remark`) VALUES
(7001,'sys.session.timeout',      '30',            '会话超时（分钟）',      1,'access token 有效期参考值'),
(7002,'sys.login.fail.limit',     '5',             '登录失败锁定次数',      1,NULL),
(7003,'sys.login.lock.minutes',   '30',            '锁定时长（分钟）',      1,NULL),
(7004,'sys.reg.no.prefix',        'REG',           '挂号单号前缀',          2,NULL),
(7005,'sys.no.date.format',       'yyyyMMdd',      '单号日期段格式',        2,NULL),
(7006,'sys.backup.cron',          '0 0 2 * * ?',   '自动备份表达式',        1,'每日 02:00'),
(7007,'sys.drug.expire.warn.days','90',            '近效期预警提前天数',    2,NULL),
(7008,'sys.stock.warn.enable',    'true',          '是否开启库存预警',      2,NULL);

-- ---------------------------------------------------------------------------
-- 8. 基础业务数据（库房 / 病区 / 床位）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `biz_warehouse` (`id`,`warehouse_name`,`warehouse_code`,`warehouse_type`,`dept_id`,`status`) VALUES
(8001,'中心药库',   'WH_CENTER', 1, 1005, 1),
(8002,'门诊西药房', 'WH_OUTP',   2, 1005, 1),
(8003,'住院药房',   'WH_INP',    2, 1005, 1);

INSERT IGNORE INTO `biz_ward` (`id`,`ward_name`,`ward_code`,`dept_id`,`floor`,`bed_count`,`status`) VALUES
(8101,'内科一病区','WARD_NK1',     1001,'3F',4,1),
(8102,'外科一病区','WARD_WK1',     1002,'4F',4,1);

INSERT IGNORE INTO `biz_bed` (`id`,`ward_id`,`bed_no`,`bed_type`,`price`,`status`) VALUES
(8201,8101,'A01',1,50.00,0),(8202,8101,'A02',1,50.00,0),
(8203,8101,'A03',2,200.00,0),(8204,8101,'A04',3,500.00,0),
(8205,8102,'B01',1,50.00,0),(8206,8102,'B02',1,50.00,0),
(8207,8102,'B03',2,200.00,0),(8208,8102,'B04',3,500.00,0);

-- ---------------------------------------------------------------------------
-- 9. 诊疗项目价格
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `biz_treat_item` (`id`,`item_code`,`item_name`,`item_type`,`unit`,`price`,`insurance_type`,`insurance_ratio`,`dept_id`,`status`,`effective_date`) VALUES
(9001,'TRT_REG_PT', '普通门诊挂号费',   1,'次',10.00, 1,80.00,1001,1,'2026-01-01'),
(9002,'TRT_REG_EX', '专家门诊挂号费',   1,'次',50.00, 1,60.00,1001,1,'2026-01-01'),
(9003,'TRT_REG_EM', '急诊挂号费',       1,'次',30.00, 1,70.00,1001,1,'2026-01-01'),
(9010,'EXM_CBC',    '血常规',           3,'项',30.00, 1,80.00,1004,1,'2026-01-01'),
(9011,'EXM_URINE',  '尿常规',           3,'项',20.00, 1,80.00,1004,1,'2026-01-01'),
(9012,'EXM_LFT',    '肝功能',           3,'项',80.00, 1,70.00,1004,1,'2026-01-01'),
(9013,'EXM_RFT',    '肾功能',           3,'项',70.00, 1,70.00,1004,1,'2026-01-01'),
(9020,'EXM_CXR',    '胸部X光正位',      2,'次',60.00, 2,60.00,1004,1,'2026-01-01'),
(9021,'EXM_CT_HEAD','头颅CT平扫',       2,'次',280.00,2,50.00,1004,1,'2026-01-01'),
(9022,'EXM_US_ABD', '腹部彩超',         2,'次',150.00,2,60.00,1004,1,'2026-01-01'),
(9030,'BED_NORMAL', '普通床位费',       4,'天',50.00, 1,80.00,1001,1,'2026-01-01'),
(9031,'BED_ICU',    '重症监护床位费',   4,'天',200.00,1,70.00,1001,1,'2026-01-01'),
(9040,'TRT_INJ_IM', '肌肉注射',         1,'次',5.00,  1,80.00,1001,1,'2026-01-01'),
(9041,'TRT_INJ_IV', '静脉输液',         1,'次',15.00, 1,80.00,1001,1,'2026-01-01');

-- ---------------------------------------------------------------------------
-- 10. 药品目录与初始库存
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `biz_drug` (`id`,`drug_code`,`drug_name`,`generic_name`,`spec`,`dosage_form`,`unit`,`manufacturer`,`purchase_price`,`sale_price`,`category`,`is_special`,`status`) VALUES
(9101,'D001','阿莫西林胶囊','阿莫西林','0.25g*24粒','胶囊剂','盒','示例药业',12.00,18.50,'抗生素',0,1),
(9102,'D002','布洛芬缓释胶囊','布洛芬','0.3g*20粒','胶囊剂','盒','示例药业',9.00,14.00,'解热镇痛',0,1),
(9103,'D003','头孢克肟分散片','头孢克肟','0.1g*12片','片剂','盒','示例药业',22.00,32.00,'抗生素',0,1),
(9104,'D004','0.9%氯化钠注射液','氯化钠','500ml','注射剂','瓶','示例药业',2.50,4.00,'输液',0,1),
(9105,'D005','感冒灵颗粒','感冒灵','10g*9袋','颗粒剂','盒','示例药业',8.00,12.50,'中成药',0,1),
(9106,'D006','盐酸哌替啶注射液','哌替啶','1ml:50mg','注射剂','支','示例药业',15.00,25.00,'麻醉药品',1,1);

INSERT IGNORE INTO `biz_stock` (`id`,`drug_id`,`warehouse_id`,`batch_no`,`quantity`,`warn_num`,`produce_date`,`expire_date`) VALUES
(9201,9101,8002,'B20260101',500,100,'2026-01-01','2028-01-01'),
(9202,9102,8002,'B20260115', 80,100,'2026-01-15','2027-12-31'),
(9203,9103,8002,'B20260201',300,100,'2026-02-01','2028-02-01'),
(9204,9104,8002,'B20260301',1000,200,'2026-03-01','2027-03-01'),
(9205,9105,8002,'B20260401',200,50,'2026-04-01','2027-10-01'),
(9206,9106,8001,'B20260501', 20,10,'2026-05-01','2027-05-01');
