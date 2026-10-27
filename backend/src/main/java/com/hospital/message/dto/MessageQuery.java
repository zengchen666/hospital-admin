package com.hospital.message.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 站内消息查询。接收人由服务端强制指定为当前用户，不接收前端传值。 */
@Getter
@Setter
public class MessageQuery extends PageQuery {

    /** 1 系统 / 2 待办 / 3 预警 */
    private Integer msgType;

    /** 0 未读 / 1 已读 */
    private Integer isRead;
}
