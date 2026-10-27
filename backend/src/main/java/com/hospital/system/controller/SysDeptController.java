package com.hospital.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.api.Result;
import com.hospital.system.dto.DeptVO;
import com.hospital.system.entity.SysDept;
import com.hospital.system.mapper.SysDeptMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科室只读接口（下拉选项用）。
 *
 * <p><b>判权用 {@code isAuthenticated()} 而不是某个权限码</b>：科室是字典型数据，
 * 不含任何敏感信息，而「用户管理里选科室」「发公告时选科室」都要用它 ——
 * 挂到 {@code system:user:query} 上会让院长发不了科室公告。</p>
 *
 * <p>只返回<b>启用</b>的科室：停用的科室不该再被选为公告受众，
 * 否则又是一次「发得出去但没人收到」的静默失效。</p>
 */
@RestController
@RequestMapping("/system/dept")
public class SysDeptController {

    private final SysDeptMapper deptMapper;

    public SysDeptController(SysDeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public Result<List<DeptVO>> list() {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getStatus, 1)
                .orderByAsc(SysDept::getSort)
                .orderByAsc(SysDept::getId));
        return Result.ok(depts.stream().map(this::toVO).toList());
    }

    private DeptVO toVO(SysDept entity) {
        return DeptVO.builder()
                .id(String.valueOf(entity.getId()))
                .parentId(entity.getParentId() == null ? null : String.valueOf(entity.getParentId()))
                .deptName(entity.getDeptName())
                .build();
    }
}
