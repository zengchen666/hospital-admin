package com.hospital.common.cache;

/**
 * Redis key 统一收口。
 *
 * <p>所有 key 必须在这里定义，禁止业务代码里拼字符串：
 * 拼错的 key 不会报错，只会静默永不命中／永不失效，排查成本极高。</p>
 *
 * <p>命名规范：{@code 模块:业务:维度}，用冒号分层，便于 {@code SCAN} 按前缀清理。</p>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 权限全局版本号（String，单调递增）。任何影响权限的变更都 INCR 它 */
    public static final String PERM_VERSION = "auth:perm:version";

    /** 登录用户缓存前缀，完整 key = auth:user:{userId} */
    private static final String USER_PREFIX = "auth:user:";

    /** 登录失败计数前缀，完整 key = auth:login:fail:{username} */
    private static final String LOGIN_FAIL_PREFIX = "auth:login:fail:";

    /** 会话白名单前缀，完整 key = auth:session:{sid} → userId；登出删除即让该会话的 refresh 失效 */
    private static final String SESSION_PREFIX = "auth:session:";

    /** refresh 轮换白名单前缀，完整 key = auth:refresh:jti:{jti} → sid；用过即删，防重放 */
    private static final String REFRESH_JTI_PREFIX = "auth:refresh:jti:";

    public static String loginUser(Long userId) {
        return USER_PREFIX + userId;
    }

    public static String loginFail(String normalizedUsername) {
        return LOGIN_FAIL_PREFIX + normalizedUsername;
    }

    public static String session(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    public static String refreshJti(String jti) {
        return REFRESH_JTI_PREFIX + jti;
    }
}
