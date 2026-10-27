package com.hospital.message.controller;

import com.hospital.common.api.Result;
import com.hospital.message.dto.WarningVO;
import com.hospital.message.service.WarningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预警中心接口。
 *
 * <p>需要 {@code message:warning:scan}：预警里带患者姓名（已脱敏）与药品库存，
 * 属于院内经营数据，不该全员可见 —— 授权给管理员、院长、药房。</p>
 */
@Validated
@RestController
@RequestMapping("/warning")
public class WarningController {

    private final WarningService warningService;

    public WarningController(WarningService warningService) {
        this.warningService = warningService;
    }

    /**
     * 预警列表，实时聚合。
     *
     * @param limit 每类最多条数，默认 20
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('message:warning:scan')")
    public Result<List<WarningVO>> list(@RequestParam(required = false, defaultValue = "20") int limit) {
        return Result.ok(warningService.list(limit));
    }

    /** 预警条数，给首页角标用 */
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('message:warning:scan')")
    public Result<Integer> count() {
        return Result.ok(warningService.count());
    }
}
