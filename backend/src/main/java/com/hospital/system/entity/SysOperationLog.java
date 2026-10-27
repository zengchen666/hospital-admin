package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 sys_operation_log。
 *
 * <p>审计表只追加，不继承 BaseEntity（无逻辑删除、无乐观锁）。</p>
 */
@Getter
@Setter
@TableName("sys_operation_log")
public class SysOperationLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String username;

    private String module;

    private String operation;

    /** 类名.方法名 */
    private String method;

    private String requestUri;

    /** 请求参数，已脱敏、已截断 */
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
