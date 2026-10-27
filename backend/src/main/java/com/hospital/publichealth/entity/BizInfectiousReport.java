package com.hospital.publichealth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 传染病报告卡 biz_infectious_report。
 *
 * <p>状态：<b>0 待上报 → 1 已上报</b>。</p>
 *
 * <p><b>这是法定文书，不是普通业务单据</b>：《传染病防治法》要求甲类及按甲类管理的
 * 传染病 2 小时内上报、乙类 24 小时内上报。因此这里定下三条硬规则：</p>
 * <ol>
 *   <li>报告日期<b>不能是未来</b> —— 提前登记等于伪造报告时间；</li>
 *   <li>同一患者同一病种<b>不允许有两条待上报记录</b> —— 重复上报会污染疾控直报系统；</li>
 *   <li>上报后<b>不允许修改或删除</b>，疾控反馈只能追加到 {@code cdcResponse}。</li>
 * </ol>
 */
@Getter
@Setter
@TableName("biz_infectious_report")
public class BizInfectiousReport extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    private String diseaseName;

    /** ICD 编码，可空（部分病种院内尚未编码） */
    private String icdCode;

    private LocalDate reportDate;

    /** 报告人（填卡医生） */
    private Long reporterId;

    /** 0 待上报 / 1 已上报 */
    private Integer reportStatus;

    /** 疾控反馈 */
    private String cdcResponse;
}
