package com.hospital.publichealth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 体检记录 biz_checkup。
 *
 * <p>状态流转：<b>0 待检 → 1 已检待出报告 → 2 已出报告</b>，全程条件更新。</p>
 *
 * <p><b>套餐名与价格必须存快照</b>：套餐会改名改价，若体检单只存 package_id，
 * 一年前买的体检套餐按今天的价去算，账就对不上了。
 * 这与处方明细存药品单价快照是同一个道理。</p>
 */
@Getter
@Setter
@TableName("biz_checkup")
public class BizCheckup extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 体检单号，TJ + 日期 + 4 位序列 */
    private String checkupNo;

    private Long patientId;

    /** 套餐 ID，散查（自选单项）时为空 */
    private Long packageId;

    /** 套餐名称快照 */
    private String packageName;

    /**
     * 体检金额快照，登记时取套餐价写入，之后不再变动。
     *
     * <p>套餐调价不该影响已经做过的体检单 —— 与处方明细存药品单价快照同理。</p>
     */
    private BigDecimal amount;

    private LocalDate checkupDate;

    /** 健康评估结论，出报告时写入 */
    private String summary;

    /** 异常项摘要，逗号分隔，列表页直接展示 */
    private String abnormalItems;

    /** 报告 PDF 的文件 ID（一期未接 MinIO，预留） */
    private Long reportFileId;

    /** 0 待检 / 1 已检待出报告 / 2 已出报告 */
    private Integer status;
}
