package com.hospital.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 递增令牌版本号，让该用户已签发的 token 立即失效。
     *
     * <p><b>为什么用原生 UPDATE 而不走 updateById</b>：
     * {@code @Version} 乐观锁只在实体携带 version 值时生效，
     * 而「禁用」「重置密码」这类局部更新只 new 一个带 id 的空实体，
     * version 为 null，拦截器不会递增。用原生 SQL 直改 token_version
     * 才能保证「改密即踢下线」一定生效。</p>
     *
     * @return 影响行数，0 表示用户不存在或已删除
     */
    @Update("""
            UPDATE sys_user
               SET token_version = token_version + 1,
                   update_by = COALESCE(#{operatorId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND deleted = 0
            """)
    int bumpTokenVersion(@Param("id") Long id, @Param("operatorId") Long operatorId);

    /**
     * 用户拥有的角色编码（多角色取并集，含 sys_user.role_id 主角色）。
     *
     * <p>注意：这里的 UNION 必须与 {@link #selectPermCodesByUserId} 保持一致。
     * 两处口径不同会出现「权限全部生效、但 roles 返回空数组」的诡异现象，
     * 前端按角色隐藏菜单时会失效且极难排查。</p>
     */
    @Select("""
            SELECT DISTINCT r.role_code
              FROM sys_role r
             WHERE r.deleted = 0
               AND r.status = 1
               AND r.id IN (
                   SELECT ur.role_id
                     FROM sys_user_role ur
                    WHERE ur.user_id = #{userId}
                      AND ur.deleted = 0
                   UNION
                   SELECT u.role_id FROM sys_user u
                    WHERE u.id = #{userId} AND u.role_id IS NOT NULL AND u.deleted = 0
               )
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 用户拥有的权限码（角色 → 角色权限 → 权限点）。
     */
    @Select("""
            SELECT DISTINCT p.perm_code
              FROM sys_permission p
              JOIN sys_role_permission rp ON rp.permission_id = p.id
             WHERE rp.deleted = 0
               AND p.deleted = 0
               AND p.status = 1
               AND rp.role_id IN (
                   SELECT r.id
                     FROM sys_role r
                     JOIN sys_user_role ur ON ur.role_id = r.id
                    WHERE ur.user_id = #{userId}
                      AND ur.deleted = 0
                      AND r.deleted = 0
                      AND r.status = 1
                   UNION
                   SELECT u.role_id FROM sys_user u
                    WHERE u.id = #{userId} AND u.role_id IS NOT NULL AND u.deleted = 0
               )
            """)
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    /**
     * 指定角色的用户 ID（主角色 + 关联角色取并集）。
     *
     * <p>两路都要查：{@code sys_user.role_id} 是主角色，
     * {@code sys_user_role} 是附加角色。只看其中一路都会漏人 ——
     * 排班下拉里少一个医生，比多一个更难被发现。</p>
     */
    @Select("""
            SELECT DISTINCT u.id
              FROM sys_user u
             WHERE u.deleted = 0
               AND u.status = 1
               AND ( u.role_id = (SELECT r.id FROM sys_role r
                                   WHERE r.role_code = #{roleCode} AND r.deleted = 0)
                     OR u.id IN (
                            SELECT ur.user_id
                              FROM sys_user_role ur
                              JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
                             WHERE ur.deleted = 0 AND r.role_code = #{roleCode}
                        )
                   )
            """)
    List<Long> selectIdsByRoleCode(@Param("roleCode") String roleCode);
}
