package com.hospital.common.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 登录用户缓存快照（存 Redis 的形态，JSON 序列化）。
 *
 * <p><b>为什么不直接缓存 {@link LoginUser}</b>：</p>
 * <ol>
 *   <li>{@code LoginUser} 用 {@code @Builder} + final 字段，没有无参构造，
 *       Jackson 反序列化会失败；改它会影响 Security 上下文语义。</li>
 *   <li>缓存里<b>刻意不含 password</b>（bcrypt 哈希没有必要扩散到缓存层和运维视野）。
 *       登录校验走 {@code loadUserByUsername} 直查库，不需要密码缓存。</li>
 * </ol>
 *
 * <p>{@code permVersion} 是写入缓存那一刻的全局权限版本号。读取时与当前版本比对，
 * 不一致即视为未命中 —— 这是「漏了主动失效」时的兜底，保证权限收回最终一致。</p>
 */
public record CachedLoginUser(
        Long userId,
        String username,
        String realName,
        Long deptId,
        Integer tokenVersion,
        Set<String> roleCodes,
        Set<String> permissions,
        String permVersion
) {

    public CachedLoginUser {
        // record 反序列化后集合可能是 null（老数据/脏数据），统一兜成空集合
        roleCodes = roleCodes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roleCodes);
        permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
    }

    public static CachedLoginUser of(LoginUser user, String permVersion) {
        return new CachedLoginUser(
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                user.getTokenVersion(),
                new LinkedHashSet<>(user.getRoleCodes()),
                new LinkedHashSet<>(user.getPermissions()),
                permVersion);
    }

    /**
     * 还原成 Security 上下文对象。
     *
     * <p>password 置空：{@link LoginUser#isCredentialsNonExpired()} 恒 true，
     * 认证已在过滤器里完成，这里不回填凭据也能正常工作。</p>
     */
    public LoginUser toLoginUser() {
        return LoginUser.builder()
                .userId(userId)
                .username(username)
                .password(null)
                .realName(realName)
                .deptId(deptId)
                .tokenVersion(tokenVersion)
                .roleCodes(new LinkedHashSet<>(roleCodes))
                .permissions(new LinkedHashSet<>(permissions))
                .build();
    }
}
