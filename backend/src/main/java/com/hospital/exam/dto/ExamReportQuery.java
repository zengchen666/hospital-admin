package com.hospital.exam.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 报告查询条件。
 *
 * <p>与 {@link ExamApplyQuery} 分开：那是按申请单查（关心「谁开了什么检查」），
 * 这是按报告查（关心「结果出了没有」），筛选维度落在报告自身的字段上。</p>
 */
@Getter
@Setter
public class ExamReportQuery extends PageQuery {

    private String reportNo;

    private String patientName;

    /** 0 草稿 / 1 已发布 */
    private Integer status;

    /** 只看危急值：0 否 / 1 是 */
    private Integer isCritical;

    /** 只看未处理的危急值，临床闭环视图 */
    private Boolean pendingCritical;
}
