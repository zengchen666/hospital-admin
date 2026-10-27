package com.hospital.finance.controller;

import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.finance.dto.DailySubmitRequest;
import com.hospital.finance.dto.DailySettleVO;
import com.hospital.finance.dto.MonthlySettleVO;
import com.hospital.finance.service.DailySettleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 日结 / 月结。
 *
 * <p>查询放行给 {@code finance:query}，<b>提交日结单独要 {@code finance:daily:submit}</b>：
 * 交班意味着「这几笔账我认了」，不能让任何一个能看报表的人替别人签字。</p>
 */
@Validated
@RestController
@RequestMapping("/finance")
public class SettleController {

    private final DailySettleService settleService;

    public SettleController(DailySettleService settleService) {
        this.settleService = settleService;
    }

    /** 日结汇总；不传日期默认今天 */
    @GetMapping("/daily")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<DailySettleVO> daily(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(settleService.daily(date));
    }

    /** 月结汇总；不传月份默认本月 */
    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('finance:query')")
    public Result<MonthlySettleVO> monthly(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") String month) {
        return Result.ok(settleService.monthly(parseMonth(month)));
    }

    @PostMapping("/daily/submit")
    @PreAuthorize("hasAuthority('finance:daily:submit')")
    @AuditLog(module = "收费管理", operation = "提交日结")
    public Result<Void> submit(@Valid @RequestBody DailySubmitRequest request) {
        settleService.submit(request);
        return Result.ok();
    }

    /**
     * YearMonth 的 @DateTimeFormat 支持不稳定，这里手工解析一次。
     *
     * <p>解析失败抛 {@code BusinessException(40001)} 而不是 IllegalArgumentException ——
     * 后者会一路冒到全局兜底变成 50001「服务器开小差」，
     * 把一个「你格式写错了」的输入错误说成服务端故障。</p>
     */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "月份格式应为 yyyy-MM");
        }
    }
}
