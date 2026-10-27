package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志视图。
 *
 * <p>{@code params} 直接透传审计切面已经脱敏截断过的内容 ——
 * <b>这里不能再脱一次</b>：审计的价值就在于能还原当时到底提交了什么，
 * 二次脱敏会把「谁把药品价格从 100 改成 1」变成「谁改了价格」。</p>
 *
 * <p>真正不该进来的是密码一类字段，那件事切面已经做掉了
 * （见 {@code OperationLogAspect} 的敏感字段名单）。</p>
 */
@Getter
@Setter
public class OperationLogVO {

    private String id;

    private String userId;

    private String username;

    private String module;

    private String operation;

    private String method;

    private String requestUri;

    private String params;

    private String ip;

    private String userAgent;

    /** 0 失败 / 1 成功 */
    private Integer status;

    private String errorMsg;

    /** 耗时（毫秒） */
    private Integer costTime;

    private LocalDateTime createTime;
}
