package com.hospital.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 熔断开关。
 *
 * <p><b>为什么需要</b>：Redis 挂掉时，每次访问缓存都要等满超时（本项目配 1s）。
 * 后台管理一个页面几十个请求，用户感知就是「系统卡死」，比直接报错还糟。
 * 熔断后一段时间内直接跳过缓存走数据库 —— 数据库扛得住，
 * 而且此时缓存本来也不可能命中。</p>
 *
 * <p><b>为什么是 30 秒而不是永久</b>：Redis 恢复后需要自动重新启用缓存，
 * 不能要求人工重启应用。30 秒足够避开绝大多数瞬时抖动。</p>
 *
 * <p>用 {@link AtomicLong} 存恢复时间点而不是布尔标记，避免「打开后没人关」。</p>
 */
@Slf4j
@Component
public class RedisGuard {

    private static final long DEGRADE_MILLIS = 30_000L;

    private final AtomicLong degradeUntil = new AtomicLong(0L);

    /** 是否处于熔断期：true 表示应直接跳过缓存 */
    public boolean isDegraded() {
        return System.currentTimeMillis() < degradeUntil.get();
    }

    /** 记录一次 Redis 访问失败，进入熔断期 */
    public void markDegraded(String scene) {
        long until = System.currentTimeMillis() + DEGRADE_MILLIS;
        long previous = degradeUntil.getAndSet(until);
        if (previous <= System.currentTimeMillis()) {
            log.warn("Redis 访问失败（{}），熔断 {} 秒内直接走数据库", scene, DEGRADE_MILLIS / 1000);
        }
    }

    /** Redis 恢复后主动复位，让缓存立刻重新生效 */
    public void markHealthy() {
        if (degradeUntil.getAndSet(0L) > 0L) {
            log.info("Redis 已恢复，缓存重新启用");
        }
    }
}
