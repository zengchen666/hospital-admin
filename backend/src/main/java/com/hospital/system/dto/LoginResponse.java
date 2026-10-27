package com.hospital.system.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * 登录返回：令牌 + 用户信息。
 *
 * <p><b>ID 一律用 String</b>：雪花 ID 是 19 位 Long，超过 JS 的
 * {@code Number.MAX_SAFE_INTEGER}（9007199254740991），以数字返回前端会静默丢精度，
 * 表现为「详情查不到 / 编辑串行」。这是本项目对外 API 的硬约定。</p>
 */
@Getter
@Builder
public class LoginResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserInfoVO userInfo;

    @Getter
    @Builder
    public static class UserInfoVO {
        private final String userId;
        private final String username;
        private final String realName;
        private final String deptId;
        private final Set<String> roles;
        private final List<String> permissions;
    }
}
