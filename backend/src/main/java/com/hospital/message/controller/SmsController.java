package com.hospital.message.controller;

import com.hospital.common.api.PageQuery;
import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.message.dto.SmsSendRequest;
import com.hospital.message.dto.SmsVO;
import com.hospital.message.service.SmsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短信接口。
 *
 * <p>发与查共用 {@code message:sms:send}：短信记录里全是手机号，
 * 能查就等于拿到了一份可导出的患者联系方式，所以不给一线角色。</p>
 */
@Validated
@RestController
@RequestMapping("/sms")
public class SmsController {

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('message:sms:send')")
    @AuditLog(module = "短信管理", operation = "发送短信")
    public Result<SmsVO> send(@Valid @RequestBody SmsSendRequest request) {
        return Result.ok(smsService.send(request));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('message:sms:send')")
    public Result<PageResult<SmsVO>> page(PageQuery query) {
        return Result.ok(smsService.page(query));
    }
}
