package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志 sys_login_log。
 *
 * <p>审计表不继承 BaseEntity：没有逻辑删除、没有乐观锁，只追加不修改。</p>
 */
@Getter
@Setter
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String ip;

    private String location;

    private String browser;

    private String os;

    /** 0 失败 / 1 成功 */
    private Integer status;

    private String msg;

    private LocalDateTime loginTime;
}
