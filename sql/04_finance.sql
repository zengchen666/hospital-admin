-- ============================================================================
-- 04 收费与财务模块（6 张）
-- biz_treat_item / biz_charge / biz_charge_item / biz_invoice
-- biz_refund / biz_insurance_settle
-- ============================================================================
USE `hospital`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 诊疗项目价格表
--    改价铁律：停用旧记录 + 新增记录，禁止 UPDATE price
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_treat_item` (
  `id`              BIGINT        NOT NULL             COMMENT '主键',
  `item_code`       VARCHAR(30)   NOT NULL             COMMENT '项目编码',
  `item_name`       VARCHAR(100)  NOT NULL             COMMENT '项目名称，如 血常规',
  `item_type`       TINYINT       NOT NULL DEFAULT 1   COMMENT '类型：1诊疗 2检查 3检验 4床位 5手术 6材料',
  `spec`            VARCHAR(50)            DEFAULT NULL COMMENT '规格',
  `unit`            VARCHAR(20)   NOT NULL DEFAULT '次' COMMENT '计价单位：次/项/天',
  `price`           DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '标准价格',
  `insurance_type`  TINYINT       NOT NULL DEFAULT 3   COMMENT '医保类别：1甲类 2乙类 3丙类（自费）',
  `insurance_ratio` DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '报销比例（%），丙类为 0',
  `dept_id`         BIGINT                 DEFAULT NULL COMMENT '执行科室ID',
  `status`          TINYINT       NOT NULL DEFAULT 1   COMMENT '状态：0停用 1启用',
  `effective_date`  DATE          NOT NULL             COMMENT '生效日期',
  `create_by`       BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT       NOT NULL DEFAULT 0   COMMENT '逻辑删除：0正常 1已删除',
  `version`         INT           NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_treat_code` (`item_code`, `effective_date`),
  KEY `idx_treat_name` (`item_name`),
  KEY `idx_treat_type` (`item_type`),
  KEY `idx_treat_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊疗项目价格表';

-- ---------------------------------------------------------------------------
-- 2. 收费单
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_charge` (
  `id`              BIGINT        NOT NULL             COMMENT '主键',
  `charge_no`       VARCHAR(30)   NOT NULL             COMMENT '收费单号',
  `patient_id`      BIGINT        NOT NULL             COMMENT '患者ID',
  `visit_id`        BIGINT                 DEFAULT NULL COMMENT '就诊ID',
  `source_type`     TINYINT       NOT NULL DEFAULT 0   COMMENT '来源类型：0无 1处方 2检查 3挂号 4住院',
  `source_id`       BIGINT                 DEFAULT NULL COMMENT '来源单ID（如处方ID）',
  `charge_type`     TINYINT       NOT NULL DEFAULT 2   COMMENT '类型：1挂号 2门诊 3住院',
  `total_amount`    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '总金额（= 明细汇总）',
  `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  `actual_amount`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实收金额',
  `pay_type`        TINYINT       NOT NULL DEFAULT 1   COMMENT '支付方式：1现金 2微信 3支付宝 4医保 5银行卡',
  `pay_status`      TINYINT       NOT NULL DEFAULT 0   COMMENT '状态：0待支付 1已支付 2已退费 3部分退费',
  `operator_id`     BIGINT                 DEFAULT NULL COMMENT '收费员',
  `charge_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收费时间',
  `create_by`       BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT       NOT NULL DEFAULT 0   COMMENT '逻辑删除：0正常 1已删除',
  `version`         INT           NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_charge_no` (`charge_no`),
  UNIQUE KEY `uk_charge_source` (`source_type`, `source_id`),
  KEY `idx_charge_patient` (`patient_id`),
  KEY `idx_charge_time` (`charge_time`),
  KEY `idx_charge_visit` (`visit_id`),
  KEY `idx_charge_operator` (`operator_id`, `charge_time`),
  KEY `idx_charge_status` (`pay_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收费单表';

-- ---------------------------------------------------------------------------
-- 3. 收费明细
--    refund_num 用于部分退费，配合 biz_refund 防止重复退
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_charge_item` (
  `id`          BIGINT        NOT NULL                COMMENT '主键',
  `charge_id`   BIGINT        NOT NULL                COMMENT '收费单ID',
  `item_type`   TINYINT       NOT NULL DEFAULT 2      COMMENT '类型：1药品 2诊疗 3检查 4床位',
  `item_id`     BIGINT                 DEFAULT NULL   COMMENT '来源项目ID（药品ID/诊疗项目ID，便于溯源）',
  `item_name`   VARCHAR(100)  NOT NULL                COMMENT '项目名称快照',
  `quantity`    INT           NOT NULL DEFAULT 0      COMMENT '数量',
  `price`       DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '单价快照',
  `amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '金额',
  `refund_num`  INT           NOT NULL DEFAULT 0      COMMENT '已退数量（禁超 quantity）',
  `create_by`   BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT                 DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删除',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_charge_item_charge` (`charge_id`),
  KEY `idx_charge_item_item` (`item_type`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收费明细表';

-- ---------------------------------------------------------------------------
-- 4. 发票表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_invoice` (
  `id`           BIGINT        NOT NULL               COMMENT '主键',
  `invoice_no`   VARCHAR(50)   NOT NULL               COMMENT '发票号',
  `charge_id`    BIGINT        NOT NULL               COMMENT '收费单ID',
  `invoice_type` TINYINT       NOT NULL DEFAULT 1     COMMENT '类型：1电子 2纸质',
  `amount`       DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '发票金额',
  `status`       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0正常 1作废',
  `print_time`   DATETIME               DEFAULT NULL  COMMENT '打印时间',
  `create_by`    BIGINT                 DEFAULT NULL  COMMENT '创建人ID',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT                 DEFAULT NULL  COMMENT '更新人ID',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1已删除',
  `version`      INT           NOT NULL DEFAULT 0     COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_invoice_charge` (`charge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票表';

-- ---------------------------------------------------------------------------
-- 5. 退费申请（审核人不得为申请人本人，由服务层校验）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_refund` (
  `id`            BIGINT        NOT NULL              COMMENT '主键',
  `refund_no`     VARCHAR(30)   NOT NULL              COMMENT '退费单号',
  `charge_id`     BIGINT        NOT NULL              COMMENT '原收费单ID',
  `patient_id`    BIGINT        NOT NULL              COMMENT '患者ID',
  `refund_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退费金额',
  `refund_type`   TINYINT       NOT NULL DEFAULT 1    COMMENT '类型：1全额 2部分',
  `reason`        VARCHAR(255)           DEFAULT NULL COMMENT '退费原因',
  `status`        TINYINT       NOT NULL DEFAULT 0    COMMENT '状态：0待审核 1已审核 2已退款 3已驳回',
  `applicant_id`  BIGINT                 DEFAULT NULL COMMENT '申请人',
  `auditor_id`    BIGINT                 DEFAULT NULL COMMENT '审核人',
  `audit_time`    DATETIME               DEFAULT NULL COMMENT '审核时间',
  `refund_time`   DATETIME               DEFAULT NULL COMMENT '退款时间',
  `original_pay`  TINYINT       NOT NULL DEFAULT 1    COMMENT '原支付方式（原路退回依据）',
  `create_by`     BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  `version`       INT           NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_refund_charge` (`charge_id`),
  KEY `idx_refund_status` (`status`),
  KEY `idx_refund_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退费申请表';

-- ---------------------------------------------------------------------------
-- 6. 医保结算（一期为 Mock，字段预留真实对接）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_insurance_settle` (
  `id`               BIGINT        NOT NULL            COMMENT '主键',
  `settle_no`        VARCHAR(50)            DEFAULT NULL COMMENT '结算单号（医保返回，Mock 时本地生成）',
  `charge_id`        BIGINT        NOT NULL            COMMENT '收费单ID',
  `insurance_type`   VARCHAR(30)            DEFAULT NULL COMMENT '医保类型：职工/居民/新农合/商保',
  `total_amount`     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '总费用',
  `insurance_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '医保支付金额',
  `self_amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '个人自付金额',
  `settle_status`    TINYINT       NOT NULL DEFAULT 0  COMMENT '状态：0待结算 1已结算 2结算失败',
  `fail_reason`      VARCHAR(255)           DEFAULT NULL COMMENT '失败原因（接口返回原文）',
  `request_body`     TEXT                   DEFAULT NULL COMMENT '上送报文（排查用）',
  `response_body`    TEXT                   DEFAULT NULL COMMENT '返回报文（排查用）',
  `settle_time`      DATETIME               DEFAULT NULL COMMENT '结算时间',
  `create_by`        BIGINT                 DEFAULT NULL COMMENT '创建人ID',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT                 DEFAULT NULL COMMENT '更新人ID',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0正常 1已删除',
  `version`          INT           NOT NULL DEFAULT 0  COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_settle_charge` (`charge_id`),
  KEY `idx_settle_no` (`settle_no`),
  KEY `idx_settle_status` (`settle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医保结算表';
