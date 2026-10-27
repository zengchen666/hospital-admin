-- ============================================================================
-- 01 系统管理模块（10 张）
-- sys_dept / sys_role / sys_menu / sys_permission
-- sys_user / sys_user_role / sys_role_permission
-- sys_dict / sys_dict_item / sys_config
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 科室表（自关联树，医院组织架构的根）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花ID）',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父级ID，顶级为 0',
  `dept_name`   VARCHAR(50)  NOT NULL                COMMENT '科室名称',
  `dept_code`   VARCHAR(50)  NOT NULL                COMMENT '科室编码',
  `dept_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1门诊 2住院 3医技',
  `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序（同级升序）',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0停用 1启用',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_dept_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- ---------------------------------------------------------------------------
-- 2. 角色表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `role_name`   VARCHAR(50)  NOT NULL                COMMENT '角色名称',
  `role_code`   VARCHAR(50)  NOT NULL                COMMENT '角色编码，如 admin / doctor',
  `remark`      VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0停用 1启用',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ---------------------------------------------------------------------------
-- 3. 菜单表（驱动前端动态路由）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单ID，顶级为 0',
  `menu_name`   VARCHAR(50)  NOT NULL                COMMENT '菜单名称',
  `menu_type`   TINYINT      NOT NULL DEFAULT 2      COMMENT '类型：1目录 2菜单 3按钮',
  `path`        VARCHAR(200)          DEFAULT NULL   COMMENT '路由地址（目录/菜单必填）',
  `component`   VARCHAR(200)          DEFAULT NULL   COMMENT '前端组件路径，如 system/user/index',
  `perms`       VARCHAR(100)          DEFAULT NULL   COMMENT '权限标识，如 system:user:add',
  `icon`        VARCHAR(50)           DEFAULT NULL   COMMENT '图标名',
  `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序',
  `visible`     TINYINT      NOT NULL DEFAULT 1      COMMENT '是否显示：0隐藏 1显示',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0停用 1正常',
  `remark`      VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_menu_parent` (`parent_id`),
  KEY `idx_menu_perms` (`perms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- ---------------------------------------------------------------------------
-- 4. 权限表（按钮级 + 数据权限的权限点定义）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `perm_code`   VARCHAR(100) NOT NULL                COMMENT '权限标识，全局唯一，如 patient:decrypt',
  `perm_name`   VARCHAR(50)  NOT NULL                COMMENT '权限名称',
  `perm_type`   TINYINT      NOT NULL DEFAULT 2      COMMENT '类型：1菜单 2按钮 3接口 4数据',
  `menu_id`     BIGINT                DEFAULT NULL   COMMENT '关联菜单ID（可空）',
  `data_scope`  TINYINT      NOT NULL DEFAULT 1      COMMENT '数据范围：1全部 2本科室 3本人 4自定义（仅 perm_type=4 有效）',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0停用 1正常',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`),
  KEY `idx_perm_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ---------------------------------------------------------------------------
-- 5. 用户表
--    注意：phone 为 AES-256-GCM 密文，故长度放到 128；phone_hash 用于精确检索
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL                COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 密文，60 字符）',
  `real_name`   VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
  `gender`      TINYINT      NOT NULL DEFAULT 0      COMMENT '性别：0女 1男',
  `phone`       VARCHAR(128)          DEFAULT NULL   COMMENT '手机号（AES-256-GCM 密文）',
  `phone_hash`  CHAR(64)              DEFAULT NULL   COMMENT '手机号 SHA-256，用于精确查询',
  `email`       VARCHAR(50)           DEFAULT NULL   COMMENT '邮箱',
  `dept_id`     BIGINT                DEFAULT NULL   COMMENT '科室ID',
  `role_id`     BIGINT                DEFAULT NULL   COMMENT '主角色ID（多角色见 sys_user_role）',
  `avatar`      VARCHAR(255)          DEFAULT NULL   COMMENT '头像URL',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0禁用 1启用',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（并发更新检测，与令牌版本分开）',
  `token_version` INT        NOT NULL DEFAULT 0      COMMENT '令牌版本号：改密/禁用时递增，令已签发 token 立即失效',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_user_dept` (`dept_id`),
  KEY `idx_user_role` (`role_id`),
  KEY `idx_user_phone_hash` (`phone_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------------------------------------------------------------------------
-- 6. 用户角色关联表（多角色，一人可多岗）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`          BIGINT   NOT NULL                    COMMENT '主键',
  `user_id`     BIGINT   NOT NULL                    COMMENT '用户ID',
  `role_id`     BIGINT   NOT NULL                    COMMENT '角色ID',
  `create_by`   BIGINT            DEFAULT NULL       COMMENT '创建人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT            DEFAULT NULL       COMMENT '更新人ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT  NOT NULL DEFAULT 0          COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT      NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ---------------------------------------------------------------------------
-- 7. 角色权限关联表（含自定义数据范围科室）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id`            BIGINT   NOT NULL                  COMMENT '主键',
  `role_id`       BIGINT   NOT NULL                  COMMENT '角色ID',
  `permission_id` BIGINT   NOT NULL                  COMMENT '权限ID',
  `data_dept_ids` TEXT              DEFAULT NULL     COMMENT '自定义数据范围科室ID，逗号分隔（data_scope=4 时使用）',
  `create_by`     BIGINT            DEFAULT NULL     COMMENT '创建人ID',
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT            DEFAULT NULL     COMMENT '更新人ID',
  `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT  NOT NULL DEFAULT 0        COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT      NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_rp_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ---------------------------------------------------------------------------
-- 8. 数据字典主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_dict` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `dict_type`   VARCHAR(50)  NOT NULL                COMMENT '字典类型，唯一，如 gender',
  `dict_name`   VARCHAR(50)  NOT NULL                COMMENT '字典名称，如 性别',
  `remark`      VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0停用 1正常',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典主表';

-- ---------------------------------------------------------------------------
-- 9. 数据字典明细表
--    已入库的 item_value 只允许改 label，禁止改 value
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `dict_type`   VARCHAR(50)  NOT NULL                COMMENT '字典类型（冗余，免联表）',
  `item_label`  VARCHAR(50)  NOT NULL                COMMENT '显示文本，如 男',
  `item_value`  VARCHAR(50)  NOT NULL                COMMENT '存储值，如 1',
  `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序',
  `css_class`   VARCHAR(50)           DEFAULT NULL   COMMENT '标签样式，如 text-danger',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0停用 1正常',
  `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_item` (`dict_type`, `item_value`),
  KEY `idx_dict_item_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典明细表';

-- ---------------------------------------------------------------------------
-- 10. 系统参数表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           BIGINT       NOT NULL               COMMENT '主键',
  `config_key`   VARCHAR(100) NOT NULL               COMMENT '参数键，唯一，如 sys.session.timeout',
  `config_value` VARCHAR(500) NOT NULL               COMMENT '参数值',
  `config_name`  VARCHAR(100) NOT NULL               COMMENT '参数名称',
  `config_type`  TINYINT      NOT NULL DEFAULT 2     COMMENT '类型：1系统内置（不可删） 2业务自定义',
  `remark`       VARCHAR(255)          DEFAULT NULL  COMMENT '备注',
  `create_by`    BIGINT                DEFAULT NULL  COMMENT '创建人ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT                DEFAULT NULL  COMMENT '更新人ID',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT          NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数表';
