package com.hospital.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.system.dto.PermissionVO;
import com.hospital.system.dto.RoleVO;
import com.hospital.system.entity.SysRole;
import com.hospital.system.entity.SysPermission;
import com.hospital.system.mapper.SysPermissionMapper;
import com.hospital.system.mapper.SysRoleMapper;
import com.hospital.system.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色查询（只读）。
 *
 * <p><b>刻意不提供增删改</b>：角色的每一条授权都应该是一次有评审、有记录的变更。
 * 做成界面上的勾选框，改起来是快了，但三个月后没人能回答
 * 「为什么收费员现在能看全院收入」—— 权限矩阵也就名存实亡。
 * 授权变更走 {@code sql/} 下的补丁脚本，脚本即记录。</p>
 */
@Slf4j
@Service
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    public SysRoleService(SysRoleMapper roleMapper,
                          SysPermissionMapper permissionMapper,
                          SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /** 全部角色（含停用），带权限数与用户数 */
    public List<RoleVO> list() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
        Map<Long, Integer> userCounts = countUsers(roles);
        return roles.stream().map(role -> toVO(role, null, userCounts)).toList();
    }

    /** 角色详情，含已授权的权限码 */
    public RoleVO detail(Long id) {
        SysRole role = requireRole(id);
        List<SysPermission> perms = permissionMapper.selectByRoleId(id);
        Map<Long, Integer> userCounts = countUsers(List.of(role));
        return toVO(role, perms, userCounts);
    }

    /** 角色的权限清单 */
    public List<PermissionVO> permissions(Long id) {
        requireRole(id);
        return permissionMapper.selectByRoleId(id).stream().map(PermissionVO::of).toList();
    }

    /**
     * 全量权限点（含停用）。
     *
     * <p>停用的权限也要列出来：否则「系统里到底定义过哪些权限」这个问题
     * 只能靠翻 SQL 补丁回答，而补丁是按时间顺序写的，不是按模块。</p>
     */
    public List<PermissionVO> allPermissions() {
        return permissionMapper.selectAll().stream().map(PermissionVO::of).toList();
    }

    /**
     * 权限总表，并标出该角色是否已持有。
     *
     * <p>前端勾选框需要「全量 + 已选」两份数据才能渲染，
     * 分开成两个接口又会出现「两边不是同一时刻查的」这种不一致。</p>
     */
    public List<PermissionVO> permissionMatrix(Long id) {
        requireRole(id);
        List<String> owned = permissionMapper.selectByRoleId(id).stream()
                .map(SysPermission::getPermCode)
                .toList();
        return permissionMapper.selectAll().stream()
                .map(PermissionVO::of)
                .peek(vo -> vo.setStatus(owned.contains(vo.getPermCode()) ? 1 : 0))
                .toList();
    }

    /* ---------------- 内部方法 ---------------- */

    private SysRole requireRole(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色ID不能为空");
        }
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "角色不存在或已删除");
        }
        return role;
    }

    /** 批量统计每个角色下的用户数，避免 N+1 */
    private Map<Long, Integer> countUsers(List<SysRole> roles) {
        Map<Long, Integer> counts = new HashMap<>();
        List<Long> ids = roles.stream().map(SysRole::getId).toList();
        if (ids.isEmpty()) {
            return counts;
        }
        List<Map<String, Object>> rows = userRoleMapper.countByRoleIds(ids);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object roleId = row.get("role_id");
                Object total = row.get("total");
                if (roleId != null) {
                    counts.put(((Number) roleId).longValue(),
                            total == null ? 0 : ((Number) total).intValue());
                }
            }
        }
        return counts;
    }

    private RoleVO toVO(SysRole role, List<SysPermission> perms, Map<Long, Integer> userCounts) {
        RoleVO vo = new RoleVO();
        vo.setId(String.valueOf(role.getId()));
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setRemark(role.getRemark());
        vo.setStatus(role.getStatus());
        vo.setPermCodes(perms == null ? null : perms.stream().map(SysPermission::getPermCode).toList());
        vo.setPermCount(perms == null ? null : perms.size());
        vo.setUserCount(userCounts.getOrDefault(role.getId(), 0));
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }
}
