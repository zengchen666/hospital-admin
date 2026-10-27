package com.hospital.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短信发送记录 sys_sms_record。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：只有 send_time，没有逻辑删除与乐观锁。</p>
 *
 * <p><b>无论发送成功还是失败都要落一条</b>：这条记录回答了
 * 「谁在什么时候给哪个手机号发了什么」，是合规凭证，不是调试日志。
 * 一期没接短信服务商，所以记录会全部是「失败 + 未接入」，
 * 但接口与落库行为先对齐，接服务商时只需换 {@code SmsSender} 实现。</p>
 */
@Getter
@Setter
@TableName("sys_sms_record")
public class SysSmsRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String phone;

    private String templateCode;

    private String content;

    /** 0 失败 / 1 成功 */
    private Integer sendStatus;

    private String failReason;

    private LocalDateTime sendTime;
}
