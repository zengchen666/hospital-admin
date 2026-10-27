package com.hospital.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.system.entity.SysPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限点查询。
 *
 * <p>权限点由 {@code sql/} 下的补丁脚本维护，<b>不提供增删改接口</b>：
 * 权限码一旦能在界面上被创建，就等于把「谁能干什么」的决策从评审过的 SQL
 * 搬到了运行时的一次点击上，事后没有任何可追溯的变更记录。</p>
 */
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 某角色已启用的权限（按角色授权表关联） */
    @Select("""
            SELECT p.* FROM sys_permission p
              JOIN sys_role_permission rp
                ON rp.permission_id = p.id AND rp.deleted = 0
             WHERE rp.role_id = #{roleId}
               AND p.deleted = 0
               AND p.status = 1
             ORDER BY p.perm_code
            """)
    List<SysPermission> selectByRoleId(@Param("roleId") Long roleId);

    /** 全量启用的权限点，供角色管理页做「已授权 / 未授权」对照 */
    @Select("""
            SELECT * FROM sys_permission
             WHERE deleted = 0
             ORDER BY perm_code
            """)
    List<SysPermission> selectAll();
}
