package com.hospital.system.controller;

import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.system.dto.BackupVO;
import com.hospital.system.service.BackupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据备份接口（需求文档模块十二「数据备份」）。
 *
 * <p><b>只有备份，没有恢复</b>。恢复会把当前数据库整体覆盖，
 * 做成一次点击的按钮，等于把删库的能力交给了任何一个有备份权限的人，
 * 而且事后没人能说清到底回到了哪个时间点。
 * 恢复是 DBA 拿着文件在维护窗口里做的事。</p>
 *
 * <p>权限：{@code security:backup:run} 执行与删除，
 * 查看历史也用同一个码 —— 备份历史里包含库名、路径、大小，
 * 本身就是该被保护的信息。</p>
 */
@RestController
@RequestMapping("/security/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('security:backup:run')")
    public Result<List<BackupVO>> list() {
        return Result.ok(backupService.list());
    }

    /**
     * 执行一次手动备份。
     *
     * <p>接口会同步等待 mysqldump 结束（上限 10 分钟），
     * 这是有意的：备份必须让调用方拿到确定的结果。
     * 改成异步的话，「点了备份但不知道成没成」比等一会儿更糟。</p>
     */
    @PostMapping("/run")
    @PreAuthorize("hasAuthority('security:backup:run')")
    @AuditLog(module = "数据安全", operation = "执行数据备份")
    public Result<Long> run() {
        return Result.ok(backupService.run());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('security:backup:run')")
    @AuditLog(module = "数据安全", operation = "删除备份记录")
    public Result<Void> delete(@PathVariable Long id) {
        backupService.delete(id);
        return Result.ok();
    }
}
