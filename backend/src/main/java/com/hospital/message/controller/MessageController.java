package com.hospital.message.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.message.dto.MessageQuery;
import com.hospital.message.dto.MessageVO;
import com.hospital.message.service.MessageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息接口。
 *
 * <p>全部接口只操作<b>当前登录用户自己的消息</b>：查询、标记已读都不接收 userId，
 * 接收人一律由服务端从登录态取。这是本模块唯一需要守住的边界 ——
 * 一旦开了口子，改个 id 就能读别人的通知。</p>
 */
@Validated
@RestController
@RequestMapping("/message")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('message:query')")
    public Result<PageResult<MessageVO>> page(MessageQuery query) {
        return Result.ok(messageService.page(query));
    }

    /** 未读数，给顶栏小红点轮询用 */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('message:query')")
    public Result<Integer> unreadCount() {
        return Result.ok(messageService.unreadCount());
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('message:query')")
    @AuditLog(module = "消息中心", operation = "标记消息已读")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('message:query')")
    @AuditLog(module = "消息中心", operation = "全部标记已读")
    public Result<Integer> markAllRead() {
        return Result.ok(messageService.markAllRead());
    }
}
