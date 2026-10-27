package com.hospital.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查检验报告 biz_exam_report（一张申请单一份报告）。
 *
 * <p>状态：0 草稿 → 1 已发布。<b>危急值</b>（{@code is_critical}）必须在发布时标记，
 * 发布后进入临床待处理列表 —— 危急值漏通知是真正的医疗事故，
 * 所以这一项不能只写在备注里，要有独立字段与独立查询。</p>
 */
@Getter
@Setter
@TableName("biz_exam_report")
public class BizExamReport extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applyId;

    private String reportNo;

    /** 结果摘要 / 图文报告链接（结构化数值在 biz_exam_item） */
    private String result;

    private String conclusion;

    /** 0 否 / 1 是 */
    private Integer isCritical;

    /** 危急值处理状态：0 未处理 / 1 已处理。只有 isCritical=1 才有意义 */
    private Integer handleStatus;

    /** 危急值处理人（临床医生） */
    private Long handlerId;

    /** 危急值处理时间 */
    private java.time.LocalDateTime handleTime;

    /** 处理措施，如「已电话通知患者复诊，调整用药」 */
    private String handleRemark;

    private Long reporterId;

    private Long auditorId;

    private LocalDateTime reportTime;

    /** 0 草稿 / 1 已发布 */
    private Integer status;
}
