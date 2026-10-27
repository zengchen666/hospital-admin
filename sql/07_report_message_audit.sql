-- ============================================================================
-- 07 统计报表 / 消息通知 / 数据安全审计模块（8 张）
-- sys_report_config / sys_report_data
-- sys_message / sys_sms_record / sys_notice
-- sys_operation_log / sys_login_log / sys_backup
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 报表配置表
--    安全设计：不存可执行 SQL，只存「数据集 + 维度/指标/过滤」的 JSON 配置，
--    SQL 由后端按模板拼装，避免 SQL 注入与越权查库。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_report_config` (
  `id`          BIGINT       NOT NULL                 COMMENT '主键',
  `report_name` VARCHAR(100) NOT NULL                 COMMENT '报表名称',
  `report_type` VARCHAR(50)  NOT NULL                 COMMENT '报表类型，如 income/outpatient',
  `config_json` TEXT                  DEFAULT NULL    COMMENT '查询配置JSON：{dataset,dimensions,metrics,filters}',
  `params`      VARCHAR(500)          DEFAULT NULL    COMMENT '可配参数定义（时间范围、科室等）',
  `creator_id`  BIGINT                DEFAULT NULL    COMMENT '创建人',
  `create_by`   BIGINT                DEFAULT NULL    COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL    COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_report_type` (`report_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表配置表（不存可执行SQL）';

-- ---------------------------------------------------------------------------
-- 2. 报表数据缓存表（预聚合结果，报表查询只读此表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_report_data` (
  `id`          BIGINT   NOT NULL                     COMMENT '主键',
  `report_id`   BIGINT   NOT NULL                     COMMENT '报表ID',
  `stat_date`   DATE     NOT NULL                     COMMENT '统计日期',
  `data_json`   LONGTEXT           DEFAULT NULL       COMMENT '数据JSON（预聚合结果）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_date` (`report_id`, `stat_date`),
  KEY `idx_report_data_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表数据缓存表';

-- ---------------------------------------------------------------------------
-- 3. 站内消息表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_message` (
  `id`          BIGINT       NOT NULL                 COMMENT '主键',
  `receiver_id` BIGINT       NOT NULL                 COMMENT '接收人ID',
  `title`       VARCHAR(100) NOT NULL                 COMMENT '标题',
  `content`     TEXT                  DEFAULT NULL    COMMENT '内容',
  `msg_type`    TINYINT      NOT NULL DEFAULT 1       COMMENT '类型：1系统 2待办 3预警',
  `biz_type`    VARCHAR(50)           DEFAULT NULL    COMMENT '业务类型（如 stock_warn/drug_expire）',
  `biz_id`      BIGINT                DEFAULT NULL    COMMENT '关联业务ID（点击跳转用）',
  `is_read`     TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0未读 1已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_message_receiver` (`receiver_id`, `is_read`, `create_time`),
  KEY `idx_message_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表（流水型，不设逻辑删除）';

-- ---------------------------------------------------------------------------
-- 4. 短信记录表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_sms_record` (
  `id`            BIGINT       NOT NULL               COMMENT '主键',
  `phone`         VARCHAR(20)  NOT NULL               COMMENT '手机号（发送时必要，按脱敏规则展示）',
  `template_code` VARCHAR(50)           DEFAULT NULL  COMMENT '模板编码',
  `content`       VARCHAR(500)          DEFAULT NULL  COMMENT '短信内容',
  `send_status`   TINYINT      NOT NULL DEFAULT 0     COMMENT '状态：0失败 1成功',
  `fail_reason`   VARCHAR(255)          DEFAULT NULL  COMMENT '失败原因',
  `send_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_sms_phone` (`phone`),
  KEY `idx_sms_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信记录表';

-- ---------------------------------------------------------------------------
-- 5. 公告表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_notice` (
  `id`           BIGINT       NOT NULL                COMMENT '主键',
  `title`        VARCHAR(100) NOT NULL                COMMENT '公告标题',
  `content`      TEXT                  DEFAULT NULL   COMMENT '公告内容',
  `notice_type`  TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1院内 2科室',
  `dept_id`      BIGINT                DEFAULT NULL   COMMENT '科室ID（科室公告必填）',
  `publisher_id` BIGINT                DEFAULT NULL   COMMENT '发布人',
  `publish_time` DATETIME              DEFAULT NULL   COMMENT '发布时间',
  `status`       TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0草稿 1已发布',
  `create_by`    BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_notice_type` (`notice_type`, `status`),
  KEY `idx_notice_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- ---------------------------------------------------------------------------
-- 6. 操作日志表（审计用，不设逻辑删除、不允许业务删除）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`          BIGINT       NOT NULL                 COMMENT '主键',
  `user_id`     BIGINT                DEFAULT NULL    COMMENT '操作用户ID',
  `username`    VARCHAR(50)           DEFAULT NULL    COMMENT '用户名',
  `module`      VARCHAR(50)           DEFAULT NULL    COMMENT '模块',
  `operation`   VARCHAR(100)          DEFAULT NULL    COMMENT '操作描述',
  `method`      VARCHAR(200)          DEFAULT NULL    COMMENT '请求方法（含类名.方法名）',
  `request_uri` VARCHAR(255)          DEFAULT NULL    COMMENT '请求路径',
  `params`      TEXT                  DEFAULT NULL    COMMENT '请求参数（已脱敏）',
  `ip`          VARCHAR(50)           DEFAULT NULL    COMMENT 'IP地址',
  `user_agent`  VARCHAR(255)          DEFAULT NULL    COMMENT '浏览器UA',
  `status`      TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0失败 1成功',
  `error_msg`   VARCHAR(500)          DEFAULT NULL    COMMENT '异常信息（失败时）',
  `cost_time`   INT                   DEFAULT NULL    COMMENT '耗时（ms）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_oplog_user_time` (`user_id`, `create_time`),
  KEY `idx_oplog_time` (`create_time`),
  KEY `idx_oplog_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表（按月归档）';

-- ---------------------------------------------------------------------------
-- 7. 登录日志表（审计用）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id`         BIGINT       NOT NULL                  COMMENT '主键',
  `username`   VARCHAR(50)           DEFAULT NULL     COMMENT '用户名',
  `ip`         VARCHAR(50)           DEFAULT NULL     COMMENT 'IP地址',
  `location`   VARCHAR(100)          DEFAULT NULL     COMMENT '登录地点',
  `browser`    VARCHAR(100)          DEFAULT NULL     COMMENT '浏览器',
  `os`         VARCHAR(50)           DEFAULT NULL     COMMENT '操作系统',
  `status`     TINYINT      NOT NULL DEFAULT 1        COMMENT '状态：0失败 1成功',
  `msg`        VARCHAR(255)          DEFAULT NULL     COMMENT '提示信息',
  `login_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_loginlog_username` (`username`, `login_time`),
  KEY `idx_loginlog_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- ---------------------------------------------------------------------------
-- 8. 数据备份表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_backup` (
  `id`          BIGINT       NOT NULL                 COMMENT '主键',
  `backup_name` VARCHAR(100) NOT NULL                 COMMENT '备份名称',
  `backup_path` VARCHAR(255)          DEFAULT NULL    COMMENT '备份路径',
  `backup_size` BIGINT                DEFAULT NULL    COMMENT '文件大小（字节）',
  `backup_type` TINYINT      NOT NULL DEFAULT 1       COMMENT '类型：1手动 2自动',
  `status`      TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0失败 1成功',
  `remark`      VARCHAR(255)          DEFAULT NULL    COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
  PRIMARY KEY (`id`),
  KEY `idx_backup_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据备份表';
