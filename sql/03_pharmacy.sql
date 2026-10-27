-- ============================================================================
-- 03 药品与药房模块（9 张）
-- biz_warehouse / biz_supplier / biz_drug / biz_stock / biz_stock_record
-- biz_purchase / biz_purchase_item / biz_dispense / biz_dispense_item
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 库房表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_warehouse` (
  `id`             BIGINT      NOT NULL               COMMENT '主键',
  `warehouse_name` VARCHAR(50) NOT NULL               COMMENT '库房名称，如 门诊西药房',
  `warehouse_code` VARCHAR(30) NOT NULL               COMMENT '库房编码',
  `warehouse_type` TINYINT     NOT NULL DEFAULT 1     COMMENT '类型：1药库 2药房 3科室备药柜',
  `dept_id`        BIGINT               DEFAULT NULL  COMMENT '所属科室ID（药房必填）',
  `manager_id`     BIGINT               DEFAULT NULL  COMMENT '负责人',
  `status`         TINYINT     NOT NULL DEFAULT 1     COMMENT '状态：0停用 1启用',
  `create_by`      BIGINT               DEFAULT NULL  COMMENT '创建人ID',
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT               DEFAULT NULL  COMMENT '更新人ID',
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`        INT         NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_warehouse_code` (`warehouse_code`),
  KEY `idx_warehouse_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库房表';

-- ---------------------------------------------------------------------------
-- 2. 供应商表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_supplier` (
  `id`            BIGINT       NOT NULL               COMMENT '主键',
  `supplier_name` VARCHAR(100) NOT NULL               COMMENT '供应商名称',
  `supplier_code` VARCHAR(30)  NOT NULL               COMMENT '供应商编码',
  `contact_name`  VARCHAR(50)           DEFAULT NULL  COMMENT '联系人',
  `contact_phone` VARCHAR(20)           DEFAULT NULL  COMMENT '联系电话',
  `address`       VARCHAR(255)          DEFAULT NULL  COMMENT '地址',
  `license_no`    VARCHAR(100)          DEFAULT NULL  COMMENT '经营许可证号',
  `credit_level`  TINYINT      NOT NULL DEFAULT 3     COMMENT '信用等级：1-5，5 最优',
  `status`        TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：0停用 1启用',
  `create_by`     BIGINT                DEFAULT NULL  COMMENT '创建人ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT                DEFAULT NULL  COMMENT '更新人ID',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT          NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`),
  KEY `idx_supplier_name` (`supplier_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ---------------------------------------------------------------------------
-- 3. 药品表
--    is_special=1 时（麻精毒麻）须走专项管理流程
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_drug` (
  `id`             BIGINT        NOT NULL             COMMENT '主键',
  `drug_code`      VARCHAR(30)   NOT NULL             COMMENT '药品编码',
  `drug_name`      VARCHAR(100)  NOT NULL             COMMENT '药品名称（商品名）',
  `generic_name`   VARCHAR(100)           DEFAULT NULL COMMENT '通用名',
  `spec`           VARCHAR(50)            DEFAULT NULL COMMENT '规格，如 0.25g*24片',
  `dosage_form`    VARCHAR(30)            DEFAULT NULL COMMENT '剂型，如 片剂/注射剂',
  `unit`           VARCHAR(20)   NOT NULL DEFAULT '盒' COMMENT '最小包装单位',
  `manufacturer`   VARCHAR(100)           DEFAULT NULL COMMENT '生产厂家',
  `approval_no`    VARCHAR(50)            DEFAULT NULL COMMENT '批准文号',
  `purchase_price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '进价',
  `sale_price`     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '售价',
  `category`       VARCHAR(50)            DEFAULT NULL COMMENT '分类，如 抗生素/疫苗',
  `is_special`     TINYINT       NOT NULL DEFAULT 0   COMMENT '是否特殊药品：0否 1麻精毒麻',
  `status`         TINYINT       NOT NULL DEFAULT 1   COMMENT '状态：0停用 1启用',
  `create_by`      BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT       NOT NULL DEFAULT 0   COMMENT '逻辑删除：0正常 1已删除',
  `version`        INT           NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drug_code` (`drug_code`),
  KEY `idx_drug_name` (`drug_name`),
  KEY `idx_drug_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品表';

-- ---------------------------------------------------------------------------
-- 4. 库存表（按 药品 + 批号 + 库房 唯一）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_stock` (
  `id`           BIGINT      NOT NULL                 COMMENT '主键',
  `drug_id`      BIGINT      NOT NULL                 COMMENT '药品ID',
  `warehouse_id` BIGINT      NOT NULL                 COMMENT '库房ID',
  `batch_no`     VARCHAR(50) NOT NULL                 COMMENT '批号',
  `quantity`     INT         NOT NULL DEFAULT 0       COMMENT '库存数量',
  `warn_num`     INT         NOT NULL DEFAULT 0       COMMENT '预警数量，低于此值触发库存预警',
  `produce_date` DATE                 DEFAULT NULL    COMMENT '生产日期',
  `expire_date`  DATE                 DEFAULT NULL    COMMENT '有效期至，近效期预警依据',
  `create_by`    BIGINT               DEFAULT NULL    COMMENT '创建人ID',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT               DEFAULT NULL    COMMENT '更新人ID',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT     NOT NULL DEFAULT 0       COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT         NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_batch` (`drug_id`, `warehouse_id`, `batch_no`),
  KEY `idx_stock_warehouse` (`warehouse_id`),
  KEY `idx_stock_expire` (`expire_date`),
  KEY `idx_stock_quantity` (`quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品库存表';

-- ---------------------------------------------------------------------------
-- 5. 出入库流水表（所有库存变动必须留痕，与 biz_stock 同事务写入）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_stock_record` (
  `id`           BIGINT       NOT NULL                COMMENT '主键',
  `drug_id`      BIGINT       NOT NULL                COMMENT '药品ID',
  `warehouse_id` BIGINT       NOT NULL                COMMENT '库房ID（变动发生的库房）',
  `record_type`  TINYINT      NOT NULL                COMMENT '类型：1入库 2出库 3盘点 4退货',
  `quantity`     INT          NOT NULL                COMMENT '变动数量（正数，方向由 record_type 决定）',
  `batch_no`     VARCHAR(50)           DEFAULT NULL   COMMENT '批号',
  `related_no`   VARCHAR(50)           DEFAULT NULL   COMMENT '来源单号：采购单号/发药单号/盘点单号',
  `operator_id`  BIGINT                DEFAULT NULL   COMMENT '操作人',
  `remark`       VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_drug_time` (`drug_id`, `create_time`),
  KEY `idx_record_warehouse` (`warehouse_id`),
  KEY `idx_record_related` (`related_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入库流水表';

-- ---------------------------------------------------------------------------
-- 6. 采购单
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_purchase` (
  `id`           BIGINT        NOT NULL               COMMENT '主键',
  `purchase_no`  VARCHAR(30)   NOT NULL               COMMENT '采购单号',
  `supplier_id`  BIGINT        NOT NULL               COMMENT '供应商ID',
  `warehouse_id` BIGINT        NOT NULL               COMMENT '目标库房ID',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '采购总金额',
  `expect_date`  DATE                   DEFAULT NULL  COMMENT '期望到货日期',
  `status`       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0草稿 1待审核 2已审核 3已入库 4已作废',
  `applicant_id` BIGINT                 DEFAULT NULL  COMMENT '申请人',
  `auditor_id`   BIGINT                 DEFAULT NULL  COMMENT '审核人',
  `audit_time`   DATETIME               DEFAULT NULL  COMMENT '审核时间',
  `remark`       VARCHAR(255)           DEFAULT NULL  COMMENT '备注',
  `create_by`    BIGINT                 DEFAULT NULL  COMMENT '创建人ID',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT                 DEFAULT NULL  COMMENT '更新人ID',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT           NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_no` (`purchase_no`),
  KEY `idx_purchase_supplier` (`supplier_id`),
  KEY `idx_purchase_status` (`status`),
  KEY `idx_purchase_warehouse` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单表';

-- ---------------------------------------------------------------------------
-- 7. 采购明细
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_purchase_item` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `purchase_id` BIGINT        NOT NULL                COMMENT '采购单ID',
  `drug_id`     BIGINT        NOT NULL                COMMENT '药品ID',
  `quantity`    INT           NOT NULL DEFAULT 0      COMMENT '采购数量',
  `price`       DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '采购单价',
  `amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '小计',
  `arrive_num`  INT           NOT NULL DEFAULT 0      COMMENT '实到数量',
  `create_by`   BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                 DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_item_purchase` (`purchase_id`),
  KEY `idx_purchase_item_drug` (`drug_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购明细表';

-- ---------------------------------------------------------------------------
-- 8. 发药记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_dispense` (
  `id`            BIGINT        NOT NULL              COMMENT '主键',
  `dispense_no`   VARCHAR(30)   NOT NULL              COMMENT '发药单号',
  `presc_id`      BIGINT                 DEFAULT NULL COMMENT '处方ID（住院发药可空）',
  `patient_id`    BIGINT        NOT NULL              COMMENT '患者ID',
  `warehouse_id`  BIGINT        NOT NULL              COMMENT '发药库房ID',
  `dispense_type` TINYINT       NOT NULL DEFAULT 1    COMMENT '类型：1门诊发药 2住院发药 3退药',
  `total_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
  `operator_id`   BIGINT                 DEFAULT NULL COMMENT '发药人',
  `dispense_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发药时间',
  `status`        TINYINT       NOT NULL DEFAULT 0    COMMENT '状态：0待发 1已发 2已退',
  `create_by`     BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT           NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispense_no` (`dispense_no`),
  KEY `idx_dispense_presc` (`presc_id`),
  KEY `idx_dispense_patient` (`patient_id`),
  KEY `idx_dispense_time` (`dispense_time`),
  KEY `idx_dispense_warehouse` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发药记录表';

-- ---------------------------------------------------------------------------
-- 9. 发药明细（batch_no 必填：按批号扣减，近效期优先）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_dispense_item` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `dispense_id` BIGINT        NOT NULL                COMMENT '发药单ID',
  `drug_id`     BIGINT        NOT NULL                COMMENT '药品ID',
  `batch_no`    VARCHAR(50)   NOT NULL                COMMENT '批号（必填，库存扣减依据）',
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
  KEY `idx_dispense_item_dispense` (`dispense_id`),
  KEY `idx_dispense_item_drug` (`drug_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发药明细表';
