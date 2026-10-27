package com.hospital.system.service;

import com.hospital.common.cache.RedisGuard;
import com.hospital.common.cache.RedisKeys;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.JwtTokenProvider;
import com.hospital.config.HospitalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录会话与 refresh 令牌白名单。
 *
 * <p><b>解决什么</b>：纯签名的 refresh token 一旦签发，在 7 天有效期内永远有效，
 * 无法撤销。医疗系统的账号被盗、员工离职场景下这是硬伤。</p>
 *
 * <p><b>两层白名单</b>：</p>
 * <ol>
 *   <li><b>会话</b> {@code auth:session:{sid}} → userId：一次登录一个会话，
 *       多设备各有各的 sid，互不影响；登出删除后该会话的 refresh 立刻失效。</li>
 *   <li><b>令牌</b> {@code auth:refresh:jti:{jti}} → sid：每次刷新轮换都换新 jti，
 *       旧 jti 用过即删。这样即使 refresh token 被窃取，也只能用一次
 *       （而且真正的持有者下次刷新时会发现自己被踢，能及时发现异常）。</li>
 * </ol>
 *
 * <p><b>为什么校验失败一律 fail-closed</b>：这些是「签发新令牌」的低频操作，
 * 不是每请求都要走的热路径。缓存不可用时拒绝刷新，用户重新登录即可；
 * 反过来说，如果放行，就可能在无法记录白名单的情况下发出无法撤销的令牌。<br>
 * 注意这不影响已登录用户：access token 的鉴权只校验版本号，
 * 走的是带熔断降级的权限缓存，Redis 故障时依然可用。</p>
 */
@Slf4j
@Service
public class AuthSessionService {

    private final StringRedisTemplate redis;
    private final RedisGuard guard;
    private final Duration sessionTtl;

    public AuthSessionService(StringRedisTemplate redis, RedisGuard guard, HospitalProperties properties) {
        this.redis = redis;
        this.guard = guard;
        // 会话存活时间与 refresh token 有效期对齐：refresh 过期了会话也没有意义
        this.sessionTtl = Duration.ofSeconds(Math.max(60L, properties.getJwt().getRefreshTokenExpireSeconds()));
    }

    /**
     * 新建会话，返回 sid。
     *
     * @throws BusinessException 50001 缓存不可用，登录失败（fail-closed）
     */
    public String createSession(Long userId) {
        String sessionId = JwtTokenProvider.newJti();
        try {
            redis.opsForValue().set(RedisKeys.session(sessionId), String.valueOf(userId), sessionTtl);
            guard.markHealthy();
            return sessionId;
        } catch (Exception e) {
            guard.markDegraded("创建登录会话");
            log.error("创建登录会话失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "登录服务暂不可用，请稍后重试");
        }
    }

    /** 会话是否仍有效（登出/超时后为 false） */
    public boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            Boolean exists = redis.hasKey(RedisKeys.session(sessionId));
            guard.markHealthy();
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            guard.markDegraded("校验登录会话");
            log.warn("校验登录会话失败，本次拒绝刷新 sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 登记新签发的 refresh 令牌（登录成功、刷新轮换时调用）。
     *
     * @throws BusinessException 50001 缓存不可用，拒绝签发
     */
    public void registerRefreshToken(String jti, String sessionId) {
        try {
            redis.opsForValue().set(RedisKeys.refreshJti(jti), sessionId, sessionTtl);
            guard.markHealthy();
        } catch (Exception e) {
            guard.markDegraded("登记 refresh 令牌");
            log.error("登记 refresh 令牌失败 sessionId={}", sessionId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "登录服务暂不可用，请稍后重试");
        }
    }

    /**
     * 消费 refresh 令牌：存在则删除并返回 true。
     *
     * <p>用 {@code DEL} 的返回值判断「是否真的删掉了」，
     * Redis 单命令天然原子，不会出现两次并发刷新都成功的竞态。</p>
     *
     * @return false 表示该 refresh 已被使用过（重放）或从未签发
     */
    public boolean consumeRefreshToken(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            Boolean deleted = redis.delete(RedisKeys.refreshJti(jti));
            guard.markHealthy();
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            guard.markDegraded("消费 refresh 令牌");
            log.warn("消费 refresh 令牌失败，本次拒绝刷新", e);
            return false;
        }
    }

    /** 销毁会话：登出时调用，该设备此后无法再刷新 */
    public void destroySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            redis.delete(RedisKeys.session(sessionId));
            guard.markHealthy();
        } catch (Exception e) {
            // 尽力而为：会话 TTL 到期后同样会失效，不至于永久残留
            guard.markDegraded("销毁登录会话");
            log.warn("销毁登录会话失败 sessionId={}", sessionId, e);
        }
    }
}
