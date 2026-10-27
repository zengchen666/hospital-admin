package com.hospital.system.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志查询条件。
 *
 * <p>审计日志的查询入口本身就是敏感操作 —— 它能看到「谁在什么时候动了什么」。
 * 因此查询条件刻意做得<b>很窄</b>：只能按人、按模块、按结果、按时间查，
 * 不提供全表关键字模糊匹配（{@code keyword} 只允许匹配操作名与 URI）。</p>
 */
@Getter
@Setter
public class OperationLogQuery extends PageQuery {

    /** 操作人用户名，精确匹配 */
    private String username;

    /** 模块名，精确匹配 */
    private String module;

    /** 操作名，模糊匹配 */
    private String operation;

    /** 请求 URI，模糊匹配 */
    private String requestUri;

    /** 0 失败 / 1 成功 */
    private Integer status;
}
