-- ============================================================================
-- 05 住院 / 检查检验模块（8 张）
-- biz_ward / biz_bed / biz_admission / biz_medical_order / biz_nursing_record
-- biz_exam_apply / biz_exam_report / biz_exam_item
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 病区表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_ward` (
  `id`          BIGINT      NOT NULL                  COMMENT '主键',
  `ward_name`   VARCHAR(50) NOT NULL                  COMMENT '病区名称，如 内科一病区',
  `ward_code`   VARCHAR(30) NOT NULL                  COMMENT '病区编码',
  `dept_id`     BIGINT      NOT NULL                  COMMENT '所属科室ID',
  `floor`       VARCHAR(20)          DEFAULT NULL     COMMENT '楼层，如 3F',
  `bed_count`   INT         NOT NULL DEFAULT 0        COMMENT '床位数（由床位表维护）',
  `nurse_head`  BIGINT               DEFAULT NULL     COMMENT '护士长（用户ID）',
  `status`      TINYINT     NOT NULL DEFAULT 1        COMMENT '状态：0停用 1启用',
  `create_by`   BIGINT               DEFAULT NULL     COMMENT '创建人ID',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT               DEFAULT NULL     COMMENT '更新人ID',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0        COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT         NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_code` (`ward_code`),
  KEY `idx_ward_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病区表';

-- ---------------------------------------------------------------------------
-- 2. 床位表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_bed` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `ward_id`     BIGINT        NOT NULL                COMMENT '病区ID',
  `bed_no`      VARCHAR(20)   NOT NULL                COMMENT '床位号，如 A01',
  `bed_type`    TINYINT       NOT NULL DEFAULT 1      COMMENT '类型：1普通 2重症 3VIP',
  `price`       DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '床位费/天',
  `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0空闲 1占用 2维修',
  `create_by`   BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                 DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_bed` (`ward_id`, `bed_no`),
  KEY `idx_bed_status` (`ward_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='床位表';

-- ---------------------------------------------------------------------------
-- 3. 住院登记表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_admission` (
  `id`             BIGINT        NOT NULL             COMMENT '主键',
  `admission_no`   VARCHAR(30)   NOT NULL             COMMENT '住院号',
  `patient_id`     BIGINT        NOT NULL             COMMENT '患者ID',
  `dept_id`        BIGINT        NOT NULL             COMMENT '科室ID',
  `bed_id`         BIGINT                 DEFAULT NULL COMMENT '床位ID（未分配时为空）',
  `doctor_id`      BIGINT                 DEFAULT NULL COMMENT '主治医生',
  `nurse_id`       BIGINT                 DEFAULT NULL COMMENT '责任护士',
  `admission_time` DATETIME      NOT NULL             COMMENT '入院时间',
  `discharge_time` DATETIME               DEFAULT NULL COMMENT '出院时间',
  `diagnosis`      VARCHAR(500)           DEFAULT NULL COMMENT '入院诊断',
  `deposit`        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '押金（预交金）',
  `status`         TINYINT       NOT NULL DEFAULT 0   COMMENT '状态：0在院 1出院 2转科',
  `create_by`      BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT       NOT NULL DEFAULT 0   COMMENT '逻辑删除：0正常 1已删除',
  `version`        INT           NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admission_no` (`admission_no`),
  KEY `idx_admission_patient` (`patient_id`),
  KEY `idx_admission_bed` (`bed_id`),
  KEY `idx_admission_doctor` (`doctor_id`),
  KEY `idx_admission_status` (`status`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='住院登记表';

-- ---------------------------------------------------------------------------
-- 4. 医嘱表（长期/临时，执行状态严格流转）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_medical_order` (
  `id`           BIGINT       NOT NULL                COMMENT '主键',
  `admission_id` BIGINT       NOT NULL                COMMENT '住院ID',
  `order_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1长期 2临时',
  `content`      VARCHAR(500) NOT NULL                COMMENT '医嘱内容',
  `drug_id`      BIGINT                DEFAULT NULL   COMMENT '药品ID（可选）',
  `dosage`       VARCHAR(50)           DEFAULT NULL   COMMENT '用量',
  `frequency`    VARCHAR(20)           DEFAULT NULL   COMMENT '频次',
  `start_time`   DATETIME     NOT NULL                COMMENT '开始时间',
  `stop_time`    DATETIME              DEFAULT NULL   COMMENT '停止时间',
  `doctor_id`    BIGINT                DEFAULT NULL   COMMENT '开嘱医生',
  `status`       TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0未执行 1已执行 2已停止',
  `create_by`    BIGINT                DEFAULT NULL   COMMENT '创建人ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT                DEFAULT NULL   COMMENT '更新人ID',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_order_admission` (`admission_id`, `status`),
  KEY `idx_order_drug` (`drug_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医嘱表';

-- ---------------------------------------------------------------------------
-- 5. 护理记录表（三类记录共用一表，查询必须带 record_type）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_nursing_record` (
  `id`             BIGINT       NOT NULL              COMMENT '主键',
  `admission_id`   BIGINT       NOT NULL              COMMENT '住院ID',
  `record_type`    TINYINT      NOT NULL DEFAULT 1    COMMENT '类型：1护理记录 2体温单 3生命体征',
  `record_time`    DATETIME     NOT NULL              COMMENT '记录时间',
  `temperature`    DECIMAL(4,1)          DEFAULT NULL COMMENT '体温（℃）',
  `pulse`          INT                   DEFAULT NULL COMMENT '脉搏（次/分）',
  `breath`         INT                   DEFAULT NULL COMMENT '呼吸（次/分）',
  `blood_pressure` VARCHAR(20)           DEFAULT NULL COMMENT '血压，如 120/80',
  `spo2`           INT                   DEFAULT NULL COMMENT '血氧饱和度（%）',
  `intake`         INT                   DEFAULT NULL COMMENT '入量（ml）',
  `output`         INT                   DEFAULT NULL COMMENT '出量（ml）',
  `content`        TEXT                  DEFAULT NULL COMMENT '护理记录内容（record_type=1 时必填）',
  `nurse_id`       BIGINT                DEFAULT NULL COMMENT '记录护士',
  `create_by`      BIGINT                DEFAULT NULL COMMENT '创建人ID',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT                DEFAULT NULL COMMENT '更新人ID',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`        INT          NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_nursing_admission` (`admission_id`, `record_time`),
  KEY `idx_nursing_type` (`record_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护理记录表';

-- ---------------------------------------------------------------------------
-- 6. 检查申请单
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_exam_apply` (
  `id`                 BIGINT       NOT NULL           COMMENT '主键',
  `apply_no`           VARCHAR(30)  NOT NULL           COMMENT '申请单号',
  `patient_id`         BIGINT       NOT NULL           COMMENT '患者ID',
  `visit_id`           BIGINT                DEFAULT NULL COMMENT '就诊ID（一期 = biz_registration.id）',
  `doctor_id`          BIGINT                DEFAULT NULL COMMENT '申请医生',
  `exam_type`          TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1检验 2检查（影像）',
  `exam_item`          VARCHAR(100)          DEFAULT NULL COMMENT '项目名称（单项目摘要，明细见 biz_exam_item）',
  `body_part`          VARCHAR(50)           DEFAULT NULL COMMENT '检查部位',
  `clinical_diagnosis` VARCHAR(500)          DEFAULT NULL COMMENT '临床诊断',
  `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待缴费 1待采样 2检验中 3已出报告',
  `apply_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `create_by`          BIGINT                DEFAULT NULL COMMENT '创建人ID',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          BIGINT                DEFAULT NULL COMMENT '更新人ID',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
  `version`            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_apply_no` (`apply_no`),
  KEY `idx_exam_patient` (`patient_id`),
  KEY `idx_exam_status` (`status`, `exam_type`),
  KEY `idx_exam_time` (`apply_time`),
  KEY `idx_exam_doctor` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查申请表';

-- ---------------------------------------------------------------------------
-- 7. 检验报告（result 仅存摘要/图文链接，结构化数值在 biz_exam_item）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_exam_report` (
  `id`          BIGINT       NOT NULL                 COMMENT '主键',
  `apply_id`    BIGINT       NOT NULL                 COMMENT '申请单ID',
  `report_no`   VARCHAR(30)  NOT NULL                 COMMENT '报告号',
  `result`      TEXT                  DEFAULT NULL    COMMENT '结果摘要/图文报告链接',
  `conclusion`  VARCHAR(500)          DEFAULT NULL    COMMENT '结论',
  `is_critical` TINYINT      NOT NULL DEFAULT 0       COMMENT '是否危急值：0否 1是',
  `reporter_id` BIGINT                DEFAULT NULL    COMMENT '报告人',
  `auditor_id`  BIGINT                DEFAULT NULL    COMMENT '审核人',
  `report_time` DATETIME              DEFAULT NULL    COMMENT '报告时间',
  `status`      TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0草稿 1已发布',
  `create_by`   BIGINT                DEFAULT NULL    COMMENT '创建人ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                DEFAULT NULL    COMMENT '更新人ID',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_report_apply` (`apply_id`),
  KEY `idx_report_critical` (`is_critical`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验报告表';

-- ---------------------------------------------------------------------------
-- 8. 检验项目明细（申请项 + 结果项，一申请 N 条）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_exam_item` (
  `id`            BIGINT       NOT NULL               COMMENT '主键',
  `apply_id`      BIGINT       NOT NULL               COMMENT '申请单ID',
  `item_code`     VARCHAR(30)  NOT NULL               COMMENT '项目编码，如 WBC',
  `item_name`     VARCHAR(100) NOT NULL               COMMENT '项目名称，如 白细胞计数',
  `result_value`  VARCHAR(50)           DEFAULT NULL  COMMENT '结果值（12.5 / 阴性）',
  `ref_range`     VARCHAR(50)           DEFAULT NULL  COMMENT '参考范围，如 4-10',
  `unit`          VARCHAR(20)           DEFAULT NULL  COMMENT '单位，如 10^9/L',
  `abnormal_flag` TINYINT      NOT NULL DEFAULT 0     COMMENT '异常标志：0正常 1偏高 2偏低 3定性异常',
  `sort`          INT          NOT NULL DEFAULT 0     COMMENT '排序（按报告模板）',
  `create_by`     BIGINT                DEFAULT NULL  COMMENT '创建人ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT                DEFAULT NULL  COMMENT '更新人ID',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT          NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_exam_item_apply` (`apply_id`, `sort`),
  KEY `idx_exam_item_code` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验项目明细表';
