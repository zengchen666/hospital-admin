package com.hospital.message.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短信记录视图对象。
 *
 * <p>{@code phone} 在列表里<b>脱敏展示</b>：短信记录含大量手机号，
 * 列表页明文铺开会变成一份可直接导出的患者联系方式。</p>
 */
@Getter
@Setter
@Builder
public class SmsVO {

    private String id;

    /** 脱敏：138****1111 */
    private String phone;

    private String templateCode;
    private String content;

    /** 0 失败 / 1 成功 */
    private Integer sendStatus;

    private String failReason;
    private LocalDateTime sendTime;
}
