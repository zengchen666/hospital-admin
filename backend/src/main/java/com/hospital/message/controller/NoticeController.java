package com.hospital.message.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.message.dto.NoticeQuery;
import com.hospital.message.dto.NoticeRequest;
import com.hospital.message.dto.NoticeVO;
import com.hospital.message.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公告接口。
 *
 * <p>权限按「看」与「管」拆开：</p>
 * <ul>
 *   <li>{@code message:query} —— 看自己可见的公告（全员）；</li>
 *   <li>{@code message:notice:edit} —— 建/改/发/删 + 看全部含草稿（管理员、院长）。</li>
 * </ul>
 *
 * <p>注意 seed 里还有一个 {@code message:notice:publish}，本模块统一用
 * {@code message:notice:edit} 做判权 —— 一个资源的写操作只该有一个权限码，
 * 两个并存会出现「能发不能改」这种半吊子授权。</p>
 */
@Validated
@RestController
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 新建公告（草稿） */
    @PostMapping
    @PreAuthorize("hasAuthority('message:notice:edit')")
    @AuditLog(module = "公告管理", operation = "新建公告")
    public Result<Long> create(@Valid @RequestBody NoticeRequest request) {
        return Result.ok(noticeService.create(request));
    }

    /** 编辑公告，仅草稿可改 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('message:notice:edit')")
    @AuditLog(module = "公告管理", operation = "编辑公告")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        noticeService.update(id, request);
        return Result.ok();
    }

    /** 发布：草稿 → 已发布 */
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('message:notice:edit')")
    @AuditLog(module = "公告管理", operation = "发布公告")
    public Result<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return Result.ok();
    }

    /** 删除，仅草稿可删 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('message:notice:edit')")
    @AuditLog(module = "公告管理", operation = "删除公告")
    public Result<Void> remove(@PathVariable Long id) {
        noticeService.remove(id);
        return Result.ok();
    }

    /** 管理端分页（含草稿） */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('message:notice:edit')")
    public Result<PageResult<NoticeVO>> page(NoticeQuery query) {
        return Result.ok(noticeService.page(query));
    }

    /** 当前用户可见的已发布公告 */
    @GetMapping("/visible")
    @PreAuthorize("hasAuthority('message:query')")
    public Result<List<NoticeVO>> visible() {
        return Result.ok(noticeService.visible());
    }
}
