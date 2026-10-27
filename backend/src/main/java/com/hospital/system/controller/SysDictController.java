package com.hospital.system.controller;

import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.system.dto.DictItemRequest;
import com.hospital.system.dto.DictItemVO;
import com.hospital.system.dto.DictQuery;
import com.hospital.system.dto.DictRequest;
import com.hospital.system.dto.DictVO;
import com.hospital.system.service.SysDictService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据字典接口（模块一「数据字典」）。
 *
 * <p>权限分两档：<b>读</b>用 {@code system:dict:query}，<b>写</b>用 {@code system:dict:edit}。
 * 字典是被业务代码引用的配置，改错一个取值可能让全院某个下拉框同时失真，
 * 因此所有写操作都进审计。</p>
 */
@Validated
@RestController
@RequestMapping("/system/dict")
public class SysDictController {

    private final SysDictService dictService;

    public SysDictController(SysDictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<List<DictVO>> list(DictQuery query) {
        return Result.ok(dictService.list(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<DictVO> detail(@PathVariable Long id) {
        return Result.ok(dictService.detail(id));
    }

    /**
     * 按类型取字典项，供下拉框使用。
     *
     * <p>{@code onlyEnabled} 默认 true：业务下拉框不该出现已停用的选项，
     * 否则使用者会选到一个系统已经不接受的取值。</p>
     */
    @GetMapping("/items/{dictType}")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<List<DictItemVO>> items(@PathVariable String dictType,
                                          @RequestParam(defaultValue = "true") boolean onlyEnabled) {
        return Result.ok(dictService.items(dictType, onlyEnabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", operation = "新建字典")
    public Result<Long> create(@Valid @RequestBody DictRequest request) {
        return Result.ok(dictService.create(request));
    }

    /**
     * 修改字典。
     *
     * <p><b>不接收 {@code dictType}</b>：字典类型是被业务代码引用的键，
     * 开放修改等于允许一次点击就让全站引用失效。</p>
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", operation = "修改字典")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DictRequest request) {
        dictService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", operation = "删除字典")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return Result.ok();
    }

    /* ---------------- 字典项 ---------------- */

    @PostMapping("/{id}/item")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", operation = "新增字典项")
    public Result<Long> createItem(@PathVariable Long id, @Valid @RequestBody DictItemRequest request) {
        return Result.ok(dictService.createItem(id, request));
    }

    @PutMapping("/{id}/item/{itemId}")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", operation = "修改字典项")
    public Result<Void> updateItem(@PathVariable Long id,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody DictItemRequest request) {
        dictService.updateItem(id, itemId, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}/item/{itemId}")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", operation = "删除字典项")
    public Result<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        dictService.deleteItem(id, itemId);
        return Result.ok();
    }
}
