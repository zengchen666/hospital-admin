package com.hospital.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用级配置（前缀 {@code hospital}）。
 *
 * <p>统一收口，避免到处 {@code @Value} 散落字符串 key。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "hospital")
public class HospitalProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        /** HS256 密钥，至少 32 字节；未配置直接启动失败，不提供默认值 */
        private String secret;
        private String issuer = "hospital-admin";
        /** access token 有效期（秒），默认 2 小时 */
        private long accessTokenExpireSeconds = 7200L;
        /** refresh token 有效期（秒），默认 7 天 */
        private long refreshTokenExpireSeconds = 604800L;
        /** 连续登录失败多少次后锁定 */
        private int loginFailLimit = 5;
        /** 锁定时长（分钟） */
        private int loginLockMinutes = 30;
    }

    @Getter
    @Setter
    public static class Security {
        /** 免鉴权路径，相对 context-path */
        private List<String> ignoreUrls = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Cors {
        /** 允许跨域的前端源，禁止使用 "*" 搭配凭证 */
        private List<String> allowedOrigins = new ArrayList<>();
        private boolean allowCredentials = true;
    }
}
