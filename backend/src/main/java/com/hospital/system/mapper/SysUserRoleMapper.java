package com.hospital.system.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户-角色关联 sys_user_role。
 *
 * <p>只提供统计查询：用户的角色分配走 {@code SysUserService}，
 * 那里要同时维护 {@code sys_user.role_id} 与关联表的一致性，
 * 单独在这里开放写操作会制造第二条不一致的路径。</p>
 */
public interface SysUserRoleMapper {

    /**
     * 批量统计各角色下的用户数。
     *
     * <p>{@code COUNT(DISTINCT user_id)} 而不是 {@code COUNT(*)}：
     * 同一个用户因为历史原因可能有多条同角色记录，
     * 直接 COUNT 会让「这个角色下有几个人」这个数字虚高。</p>
     */
    @Select("""
            <script>
            SELECT role_id, COUNT(DISTINCT user_id) AS total
              FROM sys_user_role
             WHERE deleted = 0
               AND role_id IN
               <foreach collection="roleIds" item="id" open="(" separator="," close=")">
                 #{id}
               </foreach>
             GROUP BY role_id
            </script>
            """)
    List<Map<String, Object>> countByRoleIds(@Param("roleIds") List<Long> roleIds);
}
