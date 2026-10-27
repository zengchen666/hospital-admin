-- ============================================================================
-- 06 公共卫生与健康管理模块（5 张）
-- biz_health_record / biz_vaccination / biz_infectious_report
-- biz_checkup_package / biz_checkup
-- 说明：疫苗复用药品库存模型（biz_drug.category='疫苗' + biz_stock），不另建表
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 健康档案
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_health_record` (
  `id`              BIGINT       NOT NULL             COMMENT '主键',
  `patient_id`      BIGINT       NOT NULL             COMMENT '患者ID',
  `record_no`       VARCHAR(30)  NOT NULL             COMMENT '档案号',
  `chronic_disease` VARCHAR(255)          DEFAULT NULL COMMENT '慢病（逗号分隔，仅展示用）',
  `smoking`         TINYINT      NOT NULL DEFAULT 0   COMMENT '吸烟：0否 1是',
  `drinking`        TINYINT      NOT NULL DEFAULT 0   COMMENT '饮酒：0否 1是',
  `exercise`        VARCHAR(50)           DEFAULT NULL COMMENT '运动频率',
  `create_by`       BIGINT                DEFAULT NULL COMMENT '创建人ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建档时间',
  `update_by`       BIGINT                DEFAULT NULL COMMENT '更新人ID',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0   COMMENT '逻辑删除：0正常 1已删除',
  `version`         INT          NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_health_record_no` (`record_no`),
  UNIQUE KEY `uk_health_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康档案表';

-- ---------------------------------------------------------------------------
-- 2. 疫苗接种记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_vaccination` (
  `id`             BIGINT       NOT NULL              COMMENT '主键',
  `patient_id`     BIGINT       NOT NULL              COMMENT '患者ID',
  `vaccine_name`   VARCHAR(100) NOT NULL              COMMENT '疫苗名称',
  `batch_no`       VARCHAR(50)           DEFAULT NULL COMMENT '疫苗批号（对应 biz_stock.batch_no）',
  `dose_no`        INT          NOT NULL DEFAULT 1    COMMENT '剂次',
  `vaccinate_date` DATE         NOT NULL              COMMENT '接种日期',
  `operator_id`    BIGINT                DEFAULT NULL COMMENT '接种人',
  `next_date`      DATE                  DEFAULT NULL COMMENT '下次接种日期',
  `create_by`      BIGINT                DEFAULT NULL COMMENT '创建人ID',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT                DEFAULT NULL COMMENT '更新人ID',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`        INT          NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_vaccination_patient` (`patient_id`),
  KEY `idx_vaccination_next_date` (`next_date`),
  KEY `idx_vaccination_batch` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疫苗接种表';

-- ---------------------------------------------------------------------------
-- 3. 传染病上报表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_infectious_report` (
  `id`            BIGINT       NOT NULL               COMMENT '主键',
  `patient_id`    BIGINT       NOT NULL               COMMENT '患者ID',
  `disease_name`  VARCHAR(100) NOT NULL               COMMENT '传染病名称',
  `icd_code`      VARCHAR(20)           DEFAULT NULL  COMMENT 'ICD 编码',
  `report_date`   DATE         NOT NULL               COMMENT '报告日期',
  `reporter_id`   BIGINT                DEFAULT NULL  COMMENT '报告人',
  `report_status` TINYINT      NOT NULL DEFAULT 0     COMMENT '状态：0待上报 1已上报',
  `cdc_response`  VARCHAR(255)          DEFAULT NULL  COMMENT '疾控反馈',
  `create_by`     BIGINT                DEFAULT NULL  COMMENT '创建人ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT                DEFAULT NULL  COMMENT '更新人ID',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT          NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_infectious_patient` (`patient_id`),
  KEY `idx_infectious_date` (`report_date`, `report_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传染病上报表';

-- ---------------------------------------------------------------------------
-- 4. 体检套餐
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_checkup_package` (
  `id`           BIGINT        NOT NULL               COMMENT '主键',
  `package_name` VARCHAR(100)  NOT NULL               COMMENT '套餐名称',
  `item_ids`     TEXT                   DEFAULT NULL  COMMENT '项目ID列表，逗号分隔（对应 biz_treat_item.id）',
  `price`        DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '套餐价',
  `status`       TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0停用 1启用',
  `create_by`    BIGINT                 DEFAULT NULL  COMMENT '创建人ID',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT                 DEFAULT NULL  COMMENT '更新人ID',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT           NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_package_name` (`package_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检套餐表';

-- ---------------------------------------------------------------------------
-- 5. 体检记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_checkup` (
  `id`             BIGINT       NOT NULL              COMMENT '主键',
  `checkup_no`     VARCHAR(30)  NOT NULL              COMMENT '体检单号',
  `patient_id`     BIGINT       NOT NULL              COMMENT '患者ID',
  `package_id`     BIGINT                DEFAULT NULL COMMENT '体检套餐ID（散查为空）',
  `package_name`   VARCHAR(100)          DEFAULT NULL COMMENT '套餐名称快照',
  `checkup_date`   DATE         NOT NULL              COMMENT '体检日期',
  `summary`        TEXT                  DEFAULT NULL COMMENT '健康评估结论',
  `abnormal_items` VARCHAR(500)          DEFAULT NULL COMMENT '异常项摘要（逗号分隔，列表展示用）',
  `report_file_id` BIGINT                DEFAULT NULL COMMENT '报告 PDF 的 MinIO 文件ID',
  `status`         TINYINT      NOT NULL DEFAULT 0    COMMENT '状态：0待检 1已检待出报告 2已出报告',
  `create_by`      BIGINT                DEFAULT NULL COMMENT '创建人ID',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT                DEFAULT NULL COMMENT '更新人ID',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`        INT          NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkup_no` (`checkup_no`),
  KEY `idx_checkup_patient` (`patient_id`),
  KEY `idx_checkup_date` (`checkup_date`),
  KEY `idx_checkup_package` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检记录表';
