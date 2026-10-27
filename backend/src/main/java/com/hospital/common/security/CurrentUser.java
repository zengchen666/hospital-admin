package com.hospital.common.security;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户读取工具。
 *
 * <p>统一入口，禁止在业务代码里直接摸 SecurityContextHolder。</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 未登录场景（定时任务、启动期）返回 null。 */
    public static LoginUser orNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long idOrNull() {
        LoginUser user = orNull();
        return user == null ? null : user.getUserId();
    }

    /** 需要登录态的接口用这个，未登录直接抛 40101。 */
    public static LoginUser require() {
        LoginUser user = orNull();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireId() {
        return require().getUserId();
    }
}
