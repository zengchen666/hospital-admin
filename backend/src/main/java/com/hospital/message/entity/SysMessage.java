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
 * 站内消息 sys_message。
 *
 * <p><b>刻意不继承 {@code BaseEntity}</b>：这张表是流水型的，
 * 只有 {@code create_time}，没有 update_by / deleted / version。
 * 硬套基类会让 MyBatis-Plus 拼出不存在的列直接报错（与 biz_stock_record 同理）。</p>
 *
 * <p><b>消息不可删除</b>：它是「系统告诉过你什么」的证据。
 * 用户可以标记已读，但不能让记录消失 —— 否则「我没收到过通知」就说不清了。</p>
 */
@Getter
@Setter
@TableName("sys_message")
public class SysMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收人；所有查询都必须带它，防止看到别人的消息 */
    private Long receiverId;

    private String title;

    private String content;

    /** 1 系统 / 2 待办 / 3 预警 */
    private Integer msgType;

    /** 业务类型，如 stock_warn / drug_expire / critical */
    private String bizType;

    /** 关联业务 ID，点击消息跳转用 */
    private Long bizId;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
