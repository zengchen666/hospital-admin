package com.hospital.system.controller;

import com.alibaba.excel.EasyExcel;
import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.system.dto.OperationLogQuery;
import com.hospital.system.dto.OperationLogVO;
import com.hospital.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志接口（需求文档模块十二「操作审计」）。
 *
 * <p>权限分两档：</p>
 * <ul>
 *   <li>{@code system:log:query} 看 —— 能看审计日志的人可以知道全院谁在干什么，
 *       这是个不小的权力，只给管理员与院长；</li>
 *   <li>{@code security:log:export} 导出 —— 日志一旦离开系统就无法约束用途，
 *       而且审计导出本身也要被审计，所以这里加了 {@code @AuditLog}。</li>
 * </ul>
 *
 * <p><b>没有删除接口</b>：能删日志的审计等于没有审计。</p>
 */
@Validated
@RestController
@RequestMapping("/system/oplog")
public class OperationLogController {

    private static final String[] EXPORT_HEAD = {
            "日志ID", "操作人", "模块", "操作", "请求URI", "IP", "结果", "耗时(ms)", "时间"
    };

    private final OperationLogService logService;

    public OperationLogController(OperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:log:query')")
    public Result<PageResult<OperationLogVO>> page(@Valid OperationLogQuery query) {
        return Result.ok(logService.page(query));
    }

    /**
     * 导出审计日志。
     *
     * <p>导出上限 {@value OperationLogService#EXPORT_MAX} 条，
     * 且必须带时间范围：<b>不带时间条件的全量导出既慢又没用</b> ——
     * 导出来几万行没人看，真正要查的是「某个时间段内谁动了什么」。</p>
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('security:log:export')")
    @AuditLog(module = "数据安全", operation = "导出操作日志")
    public void export(@Valid OperationLogQuery query, HttpServletResponse response) throws IOException {
        List<OperationLogVO> rows = logService.export(query);

        List<List<String>> head = new ArrayList<>();
        for (String title : EXPORT_HEAD) {
            head.add(List.of(title));
        }
        List<List<Object>> data = new ArrayList<>();
        for (OperationLogVO row : rows) {
            List<Object> line = new ArrayList<>();
            line.add(row.getId());
            line.add(row.getUsername());
            line.add(row.getModule());
            line.add(row.getOperation());
            line.add(row.getRequestUri());
            line.add(row.getIp());
            line.add(Integer.valueOf(1).equals(row.getStatus()) ? "成功" : "失败");
            line.add(row.getCostTime());
            line.add(row.getCreateTime() == null ? "" : row.getCreateTime().toString());
            data.add(line);
        }

        String fileName = "操作日志_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"oplog.xlsx\"; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));

        EasyExcel.write(response.getOutputStream()).head(head).sheet("操作日志").doWrite(data);
        response.getOutputStream().flush();
    }
}
