package com.hospital.message.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 站内消息视图对象。 */
@Getter
@Setter
@Builder
public class MessageVO {

    private String id;
    private Long receiverId;

    /** 1 系统 / 2 待办 / 3 预警 */
    private Integer msgType;
    private String msgTypeName;

    private String title;
    private String content;

    /** 业务类型，前端据此决定点击后跳哪个页面 */
    private String bizType;
    private String bizId;

    private Integer isRead;
    private LocalDateTime createTime;
}
