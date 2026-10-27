-- ============================================================================
-- 02 患者 / 挂号 / 门诊模块（9 张）
-- biz_patient / biz_patient_card / biz_patient_tag / biz_medical_record
-- biz_follow_up / biz_schedule / biz_registration
-- biz_prescription / biz_prescription_item
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 患者表
--    id_card / phone 为密文，另存 hash 列用于精确检索
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_patient` (
  `id`              BIGINT        NOT NULL              COMMENT '主键',
  `patient_no`      VARCHAR(30)   NOT NULL              COMMENT '患者编号，如 P0001',
  `name`            VARCHAR(50)   NOT NULL              COMMENT '姓名',
  `gender`          TINYINT       NOT NULL DEFAULT 0    COMMENT '性别：0女 1男 2未知',
  `birth_date`      DATE                   DEFAULT NULL COMMENT '出生日期',
  `id_card`         VARCHAR(255)           DEFAULT NULL COMMENT '身份证号（AES-256-GCM 密文）',
  `id_card_hash`    CHAR(64)               DEFAULT NULL COMMENT '身份证 SHA-256，用于查重与精确检索',
  `phone`           VARCHAR(128)           DEFAULT NULL COMMENT '手机号（AES-256-GCM 密文）',
  `phone_hash`      CHAR(64)               DEFAULT NULL COMMENT '手机号 SHA-256',
  `address`         VARCHAR(255)           DEFAULT NULL COMMENT '住址',
  `blood_type`      VARCHAR(10)            DEFAULT NULL COMMENT '血型：A/B/AB/O/未知',
  `allergy_history` VARCHAR(500)           DEFAULT NULL COMMENT '过敏史',
  `medical_history` TEXT                   DEFAULT NULL COMMENT '既往病史',
  `family_history`  TEXT                   DEFAULT NULL COMMENT '家族病史',
  `card_no`         VARCHAR(30)            DEFAULT NULL COMMENT '当前就诊卡号（冗余快照，明细见 biz_patient_card）',
  `card_balance`    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '就诊卡余额（冗余快照）',
  `tags`            VARCHAR(255)           DEFAULT NULL COMMENT '标签摘要（逗号分隔，仅用于列表展示，明细见 biz_patient_tag）',
  `create_by`       BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建档时间',
  `update_by`       BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`         INT           NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_no` (`patient_no`),
  KEY `idx_patient_name` (`name`),
  KEY `idx_patient_idcard_hash` (`id_card_hash`),
  KEY `idx_patient_phone_hash` (`phone_hash`),
  KEY `idx_patient_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者表';

-- ---------------------------------------------------------------------------
-- 2. 就诊卡表（一人多卡，挂失/补卡全程留痕）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_patient_card` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `patient_id`  BIGINT        NOT NULL                COMMENT '患者ID',
  `card_no`     VARCHAR(30)   NOT NULL                COMMENT '就诊卡号',
  `card_type`   TINYINT       NOT NULL DEFAULT 1      COMMENT '类型：1实体卡 2电子卡 3临时卡',
  `balance`     DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '卡余额',
  `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0正常 1挂失 2注销 3已补办',
  `issue_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发卡时间',
  `loss_time`   DATETIME               DEFAULT NULL   COMMENT '挂失时间',
  `operator_id` BIGINT                 DEFAULT NULL   COMMENT '发卡/操作人',
  `remark`      VARCHAR(255)           DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                 DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_card_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='就诊卡表';

-- ---------------------------------------------------------------------------
-- 3. 患者标签表（替代逗号分隔字段，符合第一范式）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_patient_tag` (
  `id`          BIGINT      NOT NULL                  COMMENT '主键',
  `patient_id`  BIGINT      NOT NULL                  COMMENT '患者ID',
  `tag_type`    TINYINT     NOT NULL DEFAULT 4        COMMENT '类型：1慢病 2VIP 3高风险 4自定义',
  `tag_name`    VARCHAR(50) NOT NULL                  COMMENT '标签名，如 高血压',
  `source`      TINYINT     NOT NULL DEFAULT 1        COMMENT '来源：1手动打标 2规则自动打标',
  `operator_id` BIGINT               DEFAULT NULL     COMMENT '打标人（自动打标为空）',
  `create_by`   BIGINT               DEFAULT NULL     COMMENT '创建人ID',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT               DEFAULT NULL     COMMENT '更新人ID',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0        COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT         NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_tag` (`patient_id`, `tag_type`, `tag_name`),
  KEY `idx_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者标签表';

-- ---------------------------------------------------------------------------
-- 4. 病历表（EMR）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_medical_record` (
  `id`              BIGINT       NOT NULL             COMMENT '主键',
  `patient_id`      BIGINT       NOT NULL             COMMENT '患者ID',
  `visit_id`        BIGINT                DEFAULT NULL COMMENT '就诊ID（一期 = biz_registration.id），一次挂号一份病历',
  `visit_no`        VARCHAR(30)  NOT NULL             COMMENT '就诊流水号',
  `visit_type`      TINYINT      NOT NULL DEFAULT 1   COMMENT '就诊类型：1门诊 2住院',
  `chief_complaint` VARCHAR(500)          DEFAULT NULL COMMENT '主诉',
  `present_illness` TEXT                  DEFAULT NULL COMMENT '现病史',
  `physical_exam`   TEXT                  DEFAULT NULL COMMENT '体格检查',
  `diagnosis`       VARCHAR(500)          DEFAULT NULL COMMENT '诊断',
  `icd_code`        VARCHAR(20)           DEFAULT NULL COMMENT 'ICD-10 编码',
  `doctor_id`       BIGINT                DEFAULT NULL COMMENT '接诊医生ID',
  `visit_time`      DATETIME     NOT NULL             COMMENT '就诊时间',
  `status`          TINYINT      NOT NULL DEFAULT 0   COMMENT '状态：0草稿 1已提交 2已归档',
  `create_by`       BIGINT                DEFAULT NULL COMMENT '创建人ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT                DEFAULT NULL COMMENT '更新人ID',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0   COMMENT '逻辑删除：0正常 1已删除',
  `version`         INT          NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_visit_no` (`visit_no`),
  UNIQUE KEY `uk_record_visit` (`visit_id`),
  KEY `idx_record_patient` (`patient_id`),
  KEY `idx_record_doctor` (`doctor_id`),
  KEY `idx_record_time` (`visit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病历表（EMR）';

-- ---------------------------------------------------------------------------
-- 5. 随访表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_follow_up` (
  `id`          BIGINT       NOT NULL                 COMMENT '主键',
  `patient_id`  BIGINT       NOT NULL                 COMMENT '患者ID',
  `plan_date`   DATE         NOT NULL                 COMMENT '计划随访日期',
  `follow_type` TINYINT      NOT NULL DEFAULT 1       COMMENT '方式：1电话 2上门 3短信',
  `content`     TEXT                  DEFAULT NULL    COMMENT '随访内容',
  `result`      VARCHAR(500)          DEFAULT NULL    COMMENT '随访结果',
  `operator_id` BIGINT                DEFAULT NULL    COMMENT '随访人',
  `status`      TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0待随访 1已完成',
  `create_by`   BIGINT                DEFAULT NULL    COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL    COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_follow_patient` (`patient_id`),
  KEY `idx_follow_plan_date` (`plan_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访表';

-- ---------------------------------------------------------------------------
-- 6. 排班表（号源载体，扣减必须用条件更新防超卖）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_schedule` (
  `id`            BIGINT        NOT NULL              COMMENT '主键',
  `doctor_id`     BIGINT        NOT NULL              COMMENT '医生ID',
  `dept_id`       BIGINT        NOT NULL              COMMENT '科室ID',
  `schedule_date` DATE          NOT NULL              COMMENT '排班日期',
  `time_period`   TINYINT       NOT NULL              COMMENT '时段：1上午 2下午 3晚上',
  `reg_type`      TINYINT       NOT NULL DEFAULT 1    COMMENT '号别：1普通 2专家 3急诊',
  `total_num`     INT           NOT NULL DEFAULT 0    COMMENT '总号源',
  `remain_num`    INT           NOT NULL DEFAULT 0    COMMENT '剩余号源（扣减走条件更新，禁止先查后改）',
  `reg_fee`       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '挂号费',
  `status`        TINYINT       NOT NULL DEFAULT 1    COMMENT '状态：0停诊 1正常',
  `create_by`     BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT           NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule` (`doctor_id`, `schedule_date`, `time_period`, `reg_type`),
  KEY `idx_schedule_dept_date` (`dept_id`, `schedule_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生排班表';

-- ---------------------------------------------------------------------------
-- 7. 挂号记录表（一期以本表 id 充当 visit_id）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_registration` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `reg_no`      VARCHAR(30)   NOT NULL                COMMENT '挂号单号',
  `patient_id`  BIGINT        NOT NULL                COMMENT '患者ID',
  `doctor_id`   BIGINT        NOT NULL                COMMENT '医生ID',
  `dept_id`     BIGINT        NOT NULL                COMMENT '科室ID',
  `schedule_id` BIGINT        NOT NULL                COMMENT '排班ID',
  `reg_type`    TINYINT       NOT NULL DEFAULT 1      COMMENT '号别：1普通 2专家 3急诊',
  `reg_fee`     DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '挂号费',
  `queue_no`    INT                    DEFAULT NULL   COMMENT '排队号',
  `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0待就诊 1就诊中 2已完成 3已退号',
  `reg_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '挂号时间',
  `create_by`   BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                 DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reg_no` (`reg_no`),
  KEY `idx_reg_patient` (`patient_id`, `reg_time`),
  KEY `idx_reg_doctor` (`doctor_id`),
  KEY `idx_reg_dept_status` (`dept_id`, `status`),
  KEY `idx_reg_schedule` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='挂号记录表';

-- ---------------------------------------------------------------------------
-- 8. 处方表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_prescription` (
  `id`           BIGINT        NOT NULL               COMMENT '主键',
  `presc_no`     VARCHAR(30)   NOT NULL               COMMENT '处方号',
  `patient_id`   BIGINT        NOT NULL               COMMENT '患者ID',
  `doctor_id`    BIGINT        NOT NULL               COMMENT '开方医生ID',
  `visit_id`     BIGINT        NOT NULL               COMMENT '就诊ID（一期 = biz_registration.id）',
  `presc_type`   TINYINT       NOT NULL DEFAULT 1     COMMENT '类型：1西药 2中药 3中成药',
  `diagnosis`    VARCHAR(500)           DEFAULT NULL  COMMENT '临床诊断',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '处方总金额',
  `status`       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0待审核 1已审核 2已收费 3已发药 4已退',
  `create_by`    BIGINT                 DEFAULT NULL  COMMENT '创建人ID',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开方时间',
  `update_by`    BIGINT                 DEFAULT NULL  COMMENT '更新人ID',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT           NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_presc_no` (`presc_no`),
  KEY `idx_presc_patient` (`patient_id`),
  KEY `idx_presc_visit` (`visit_id`),
  KEY `idx_presc_doctor` (`doctor_id`, `create_time`),
  KEY `idx_presc_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方表';

-- ---------------------------------------------------------------------------
-- 9. 处方明细表（保留药品名称/规格快照，避免药品改名影响历史处方）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_prescription_item` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `presc_id`    BIGINT        NOT NULL                COMMENT '处方ID',
  `drug_id`     BIGINT        NOT NULL                COMMENT '药品ID',
  `drug_name`   VARCHAR(100)  NOT NULL                COMMENT '药品名称快照',
  `spec`        VARCHAR(50)            DEFAULT NULL   COMMENT '规格快照',
  `usage`       VARCHAR(100)           DEFAULT NULL   COMMENT '用法，如 口服',
  `dosage`      VARCHAR(50)            DEFAULT NULL   COMMENT '单次用量',
  `frequency`   VARCHAR(20)            DEFAULT NULL   COMMENT '频次，如 每日3次',
  `days`        INT                    DEFAULT NULL   COMMENT '用药天数',
  `quantity`    INT           NOT NULL DEFAULT 0      COMMENT '数量',
  `price`       DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '单价快照',
  `amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '小计',
  `create_by`   BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                 DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_presc_item_presc` (`presc_id`),
  KEY `idx_presc_item_drug` (`drug_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方明细表';
