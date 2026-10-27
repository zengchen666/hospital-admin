package com.hospital.common.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 登录用户上下文对象（存入 SecurityContext，随请求生命周期存在）。
 *
 * <p>只放鉴权必需的信息，不要把整个 sys_user 实体塞进来。</p>
 */
@Getter
@Builder(toBuilder = true)
public class LoginUser implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final String realName;
    private final Long deptId;
    /** 会话版本，用于改密后强制下线 */
    private final Integer tokenVersion;

    @Builder.Default
    private final Set<String> roleCodes = new LinkedHashSet<>();

    /** 权限码，如 system:user:add */
    @Builder.Default
    private final Set<String> permissions = new LinkedHashSet<>();

    /**
     * 当前请求所属的会话 ID（来自 token 的 sid）。
     *
     * <p>它是<b>请求级</b>信息，不进缓存（{@link CachedLoginUser} 里没有这个字段），
     * 由 JWT 过滤器在还原登录态时通过 {@code toBuilder()} 注入，
     * 登出时据此精确定位要销毁的那个会话（多设备互不影响）。</p>
     */
    private final String sessionId;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        // 角色统一加 ROLE_ 前缀，配合 hasRole() 使用
        roleCodes.forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
        // 权限码原样注册，配合 hasAuthority() 使用
        permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
