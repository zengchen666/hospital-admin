package com.hospital.system.service;

import com.hospital.common.cache.RedisGuard;
import com.hospital.common.cache.RedisKeys;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.config.HospitalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 登录失败锁定：连续失败 N 次锁定 M 分钟。
 *
 * <p><b>为什么必须有这个</b>：没有锁定，bcrypt 也挡不住在线暴力破解 ——
 * 攻击者可以拿字典慢慢撞，系统只会「如实回答密码错误」。</p>
 *
 * <p><b>为什么放 Redis 而不是内存 Map</b>：</p>
 * <ol>
 *   <li>多实例部署时内存计数各自为政，5 次限制变成「5 × 实例数」次；</li>
 *   <li>重启即清零，攻击者只要等到发布窗口就能重新获得满额尝试次数；</li>
 *   <li>Redis 的 {@code EXPIRE} 天然实现「到期自动解锁」，不用自己写清理逻辑。</li>
 * </ol>
 *
 * <p><b>为什么账号不存在也计数</b>：否则「是否被锁」就成了账号存在性探针。</p>
 *
 * <p><b>降级策略</b>：Redis 不可用（熔断期）时跳过锁定校验，即 fail-open。
 * 这是有意识的取舍：缓存故障导致全院登录不进来，远比短时间内缺少频率限制严重。
 * 该分支每次都打 warn 日志，配合 Redis 自身的可用性监控可以及时发现。</p>
 */
@Slf4j
@Component
public class LoginAttemptService {

    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final StringRedisTemplate redis;
    private final RedisGuard guard;
    private final int maxFail;
    private final Duration lockDuration;
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    public LoginAttemptService(StringRedisTemplate redis, RedisGuard guard, HospitalProperties properties) {
        this.redis = redis;
        this.guard = guard;
        this.maxFail = Math.max(1, properties.getJwt().getLoginFailLimit());
        this.lockDuration = Duration.ofMinutes(Math.max(1, properties.getJwt().getLoginLockMinutes()));
    }

    /**
     * 登录前调用：已锁定则直接拒绝，不再查库、不再校验密码。
     *
     * @throws BusinessException 40103
     */
    public void assertNotLocked(String username) {
        if (guard.isDegraded()) {
            return;
        }
        String key = RedisKeys.loginFail(normalize(username));
        int count;
        long remainSeconds;
        try {
            String value = redis.opsForValue().get(key);
            guard.markHealthy();
            if (value == null) {
                return;
            }
            count = parseCount(value);
            if (count < maxFail) {
                return;
            }
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            remainSeconds = (ttl == null || ttl < 0) ? lockDuration.toSeconds() : ttl;
        } catch (Exception e) {
            guard.markDegraded("读取登录失败计数");
            warnThrottled("读取登录失败计数失败，本次不做锁定校验 username=" + username, e);
            return;
        }

        long remainMinutes = Math.max(1, (remainSeconds + 59) / 60);
        log.warn("账号 {} 处于锁定期，剩余约 {} 分钟", username, remainMinutes);
        throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                "登录失败次数过多，请 " + remainMinutes + " 分钟后再试");
    }

    /** 登录失败（密码错、账号不存在、账号禁用）后调用 */
    public void recordFailure(String username) {
        if (guard.isDegraded()) {
            return;
        }
        String key = RedisKeys.loginFail(normalize(username));
        try {
            Long count = redis.opsForValue().increment(key);
            guard.markHealthy();
            if (count == null) {
                return;
            }
            // 第一次失败设置窗口；刚好达到阈值时把 TTL 重置为完整锁定时长，
            // 让「锁定」从触发那一刻起算。之后继续失败不再延长，避免攻击者
            // 无限刷新 TTL 把合法用户永久锁死。
            if (count == 1L || count == maxFail) {
                redis.expire(key, lockDuration);
            }
            if (count >= maxFail) {
                log.warn("账号 {} 连续登录失败 {} 次，已锁定 {} 分钟", username, count, lockDuration.toMinutes());
            }
        } catch (Exception e) {
            guard.markDegraded("写入登录失败计数");
            warnThrottled("写入登录失败计数失败 username=" + username, e);
        }
    }

    /** 登录成功后清零。低频写路径，不参与熔断跳过 */
    public void reset(String username) {
        try {
            redis.delete(RedisKeys.loginFail(normalize(username)));
            guard.markHealthy();
        } catch (Exception e) {
            guard.markDegraded("清除登录失败计数");
            warnThrottled("清除登录失败计数失败 username=" + username, e);
        }
    }

    /** 账号名规范化：大小写与首尾空格不影响计数，否则换个大小写就能绕过限制 */
    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private int parseCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // 脏数据：当作 0，下次失败会重新计数
            log.warn("登录失败计数非数字，已忽略 value={}", value);
            return 0;
        }
    }

    private void warnThrottled(String message, Exception e) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("{}（同类告警 {}s 内只打一次）", message, WARN_INTERVAL_MILLIS / 1000, e);
        }
    }
}
