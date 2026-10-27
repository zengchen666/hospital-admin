package com.hospital.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.message.dto.MessageQuery;
import com.hospital.message.dto.MessageVO;
import com.hospital.message.entity.SysMessage;
import com.hospital.message.mapper.SysMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 站内消息服务。
 *
 * <p>三条必须守住的规矩：</p>
 * <ol>
 *   <li><b>接收人一律取当前登录用户，不接收前端传值</b> —— 传了就变成能看别人的消息；</li>
 *   <li>标记已读的 SQL 必须带 {@code receiver_id} —— 越权请求自然 0 行 → 40900；</li>
 *   <li><b>消息不可删除</b>（表也没给逻辑删除字段）—— 它是「系统告诉过你什么」的证据。</li>
 * </ol>
 *
 * <p>{@link #sendTo} 是给其它模块用的入口：业务事件发生时推一条站内消息。
 * 调用方应当用 try-catch 包住它 —— <b>发消息失败不该让主业务回滚</b>，
 * 患者不能因为通知没发出去就看不成病。</p>
 */
@Slf4j
@Service
public class MessageService {

    /** 1 系统 / 2 待办 / 3 预警 */
    public static final int TYPE_SYSTEM = 1;
    public static final int TYPE_TODO = 2;
    public static final int TYPE_WARNING = 3;

    private final SysMessageMapper messageMapper;

    public MessageService(SysMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    /**
     * 发一条站内消息。
     *
     * @return 消息 ID；调用方若不关心可忽略
     */
    @Transactional(rollbackFor = Exception.class)
    public Long sendTo(Long receiverId, String title, String content,
                       Integer msgType, String bizType, Long bizId) {
        if (receiverId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "消息接收人不能为空");
        }
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "消息标题不能为空");
        }

        SysMessage entity = new SysMessage();
        entity.setReceiverId(receiverId);
        entity.setTitle(title.trim());
        entity.setContent(content);
        entity.setMsgType(msgType == null ? TYPE_SYSTEM : msgType);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setIsRead(0);
        entity.setCreateTime(java.time.LocalDateTime.now());
        messageMapper.insert(entity);
        return entity.getId();
    }

    public PageResult<MessageVO> page(MessageQuery query) {
        Long receiverId = CurrentUser.requireId();
        LambdaQueryWrapper<SysMessage> wrapper = Wrappers.<SysMessage>lambdaQuery()
                // 只查自己的，这一条不能省
                .eq(SysMessage::getReceiverId, receiverId)
                .eq(query.getMsgType() != null, SysMessage::getMsgType, query.getMsgType())
                .eq(query.getIsRead() != null, SysMessage::getIsRead, query.getIsRead());

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(SysMessage::getTitle, query.getKeyword().trim());
        }
        wrapper.orderByAsc(SysMessage::getIsRead).orderByDesc(SysMessage::getCreateTime);

        Page<SysMessage> page = messageMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    public int unreadCount() {
        return messageMapper.countUnread(CurrentUser.requireId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        Long receiverId = CurrentUser.requireId();
        // 已是已读也返回成功（幂等），只有「不是你的消息」才报错
        SysMessage entity = messageMapper.selectById(id);
        if (entity == null || !receiverId.equals(entity.getReceiverId())) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "消息不存在或无权访问");
        }
        if (Integer.valueOf(1).equals(entity.getIsRead())) {
            return;
        }
        if (messageMapper.markRead(id, receiverId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "消息状态已变更，请刷新后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int markAllRead() {
        return messageMapper.markAllRead(CurrentUser.requireId());
    }

    private MessageVO toVO(SysMessage entity) {
        return MessageVO.builder()
                .id(String.valueOf(entity.getId()))
                .receiverId(entity.getReceiverId())
                .msgType(entity.getMsgType())
                .msgTypeName(typeName(entity.getMsgType()))
                .title(entity.getTitle())
                .content(entity.getContent())
                .bizType(entity.getBizType())
                .bizId(entity.getBizId() == null ? null : String.valueOf(entity.getBizId()))
                .isRead(entity.getIsRead())
                .createTime(entity.getCreateTime())
                .build();
    }

    private String typeName(Integer msgType) {
        return switch (msgType == null ? 0 : msgType) {
            case TYPE_SYSTEM -> "系统";
            case TYPE_TODO -> "待办";
            case TYPE_WARNING -> "预警";
            default -> "未知";
        };
    }
}
