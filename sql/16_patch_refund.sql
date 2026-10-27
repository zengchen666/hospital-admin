-- =============================================================================
-- 退费明细表 + 退费/退药相关权限核对
--
-- 为什么必须有明细表：biz_refund 只有一个 refund_amount 总额。
-- 「退了多少钱」能记，「退的是哪一项、退了几件」记不了 ——
-- 财务对账时说不清 120 块里哪些是药、哪些是检查，也没法逐项回冲
-- biz_charge_item.refund_num 和库存。所以补一张明细表。
--
-- 权限：finance:refund:apply(3037) / finance:refund:audit(3038) / pharmacy:return(3031)
--       三个权限 seed 里已有，本补丁只做核对，不重复定义。
-- =============================================================================

USE `hospital`;

CREATE TABLE IF NOT EXISTS `biz_refund_item` (
  `id`             BIGINT        NOT NULL              COMMENT '主键',
  `refund_id`      BIGINT        NOT NULL              COMMENT '退费单ID',
  `charge_item_id` BIGINT        NOT NULL              COMMENT '原收费明细ID（回冲与防超退的依据）',
  `item_type`      TINYINT                  DEFAULT NULL COMMENT '项目类型快照：1药品 2诊疗 3检查 4床位',
  `item_name`      VARCHAR(100)             DEFAULT NULL COMMENT '项目名称快照',
  `quantity`       INT           NOT NULL DEFAULT 0    COMMENT '退数量',
  `amount`         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退金额',
  `create_by`      BIGINT                  DEFAULT NULL COMMENT '创建人ID',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT                  DEFAULT NULL COMMENT '更新人ID',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0正常 1已删除',
  -- 实体继承 BaseEntity，MyBatis-Plus 会自动拼 version 列；漏了这张表就是
  -- 「Unknown column 'version' in 'field list'」运行时炸。流水型表同理。
  `version`        INT           NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_refund_item_refund` (`refund_id`),
  KEY `idx_refund_item_charge` (`charge_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退费明细表';

-- ---------------------------------------------------------------------------
-- 核对：退费相关权限必须存在，且申请与审核是两个码（互相制衡）
-- ---------------------------------------------------------------------------
SELECT `id`, `perm_code`, `perm_name`, `status`
  FROM `sys_permission`
 WHERE `perm_code` IN ('finance:refund:apply', 'finance:refund:audit', 'pharmacy:return')
   AND `deleted` = 0;
