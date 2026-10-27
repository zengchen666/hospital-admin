package com.hospital.system.service;

import com.hospital.common.cache.RedisGuard;
import com.hospital.common.cache.RedisKeys;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CachedLoginUser;
import com.hospital.common.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 登录用户权限缓存。
 *
 * <p><b>解决的问题</b>：JWT 过滤器每次请求都要加载用户 + 角色 + 权限，
 * 原本是 3 次查库（{@code selectById} + {@code selectRoleCodesByUserId} +
 * {@code selectPermCodesByUserId}）。QPS 一上来数据库先扛不住，
 * 而这三张表读多写极少，是典型的缓存场景。</p>
 *
 * <p><b>一致性策略</b>（医疗系统权限收回必须准，不能只靠 TTL 兜）：</p>
 * <ul>
 *   <li><b>精确失效</b>：{@link #evict(Long)} 删除单个用户 key，用于该用户自身的变更。</li>
 *   <li><b>全局版本号</b>：{@link #bumpPermissionVersion()} 递增 {@code auth:perm:version}，
 *       缓存里记录了写入时的版本，读取时版本不一致即视为未命中。
 *       用于「一次变更影响一批人」（角色授权、菜单权限调整），
 *       即使漏掉精确失效也不会读到旧权限。</li>
 *   <li><b>补偿删除</b>：Redis 故障期间发生失效操作会先记入本地待删集合，
 *       恢复后第一次访问时补删，消除「故障窗口内改了权限、恢复后仍读到旧缓存」的空隙。</li>
 * </ul>
 *
 * <p><b>可用性策略</b>：读缓存任何异常都降级查库并触发熔断（见 {@link RedisGuard}），
 * 缓存故障不能让整个系统登不上、卡住；但<b>写版本号失败必须抛错</b>（fail-closed）——
 * 「权限改成功了其实没生效」比「这次修改失败请重试」危险得多。</p>
 */
@Slf4j
@Service
public class LoginUserCacheService {

    /** 基础 TTL：权限缓存 5 分钟，即使所有主动失效都漏了也能自愈 */
    private static final Duration BASE_TTL = Duration.ofMinutes(5);
    /** TTL 抖动上限（秒）：避免同一批用户缓存同时过期造成回源尖峰 */
    private static final long JITTER_SECONDS = 30L;
    /** 告警限流间隔：Redis 挂了不能每请求刷一条日志，否则磁盘先爆 */
    private static final long WARN_INTERVAL_MILLIS = 60_000L;
    /** 待补偿删除集合上限：正常只在 Redis 故障窗口内增长，设上限防极端情况失控 */
    private static final int MAX_PENDING_EVICT = 10_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisGuard guard;
    private final AtomicLong lastWarnAt = new AtomicLong(0L);
    /** Redis 故障期间未能删除的 userId，恢复后补偿删除 */
    private final Set<Long> pendingEvict = ConcurrentHashMap.newKeySet();

    public LoginUserCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, RedisGuard guard) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.guard = guard;
    }

    /**
     * 取登录用户：缓存优先，未命中/版本过期则回源并回填。
     *
     * @param userId   用户 ID
     * @param dbLoader 回源函数（由调用方提供，避免与 SysUserDetailsService 循环依赖）
     */
    public LoginUser get(Long userId, Function<Long, LoginUser> dbLoader) {
        // 熔断期：直接查库，不再浪费一次超时等待
        if (guard.isDegraded()) {
            return dbLoader.apply(userId);
        }
        flushPendingEvictions();

        String key = RedisKeys.loginUser(userId);
        String currentVersion;
        try {
            currentVersion = redis.opsForValue().get(RedisKeys.PERM_VERSION);
            String json = redis.opsForValue().get(key);
            guard.markHealthy();
            if (json != null) {
                CachedLoginUser cached = objectMapper.readValue(json, CachedLoginUser.class);
                if (Objects.equals(cached.permVersion(), currentVersion)) {
                    return cached.toLoginUser();
                }
                log.debug("权限版本已变更，缓存作废 userId={} cachedVersion={} currentVersion={}",
                        userId, cached.permVersion(), currentVersion);
            }
        } catch (Exception e) {
            // 降级：Redis 不可用不能阻断登录，直接查库
            guard.markDegraded("读取登录用户缓存");
            warnThrottled("读取登录用户缓存失败，本次降级查库 userId=" + userId, e);
            return dbLoader.apply(userId);
        }

        // 回源：账号不存在/被禁用会在这里抛 BusinessException，不写缓存
        LoginUser fresh = dbLoader.apply(userId);
        writeCache(key, fresh, currentVersion);
        return fresh;
    }

    /**
     * 强制回源：先删缓存再加载。
     *
     * <p>用于「本次必须读到最新权限」的少数场景 —— 登录成功、刷新令牌。
     * 这两处拿到的 userInfo 会原样回给前端用于渲染菜单和按钮，
     * 宁可多查一次库，也不要返回过期的角色/权限列表。</p>
     */
    public LoginUser reload(Long userId, Function<Long, LoginUser> dbLoader) {
        evict(userId);
        return get(userId, dbLoader);
    }

    /**
     * 精确失效：删除指定用户的缓存。
     *
     * <p>用于「只影响一个人」的变更（改资料、禁用、改密、删除）。
     * 属于低频写路径，<b>不参与熔断跳过</b>：即使 Redis 故障也要尽力尝试，
     * 失败则记入待补偿集合，恢复后补删。</p>
     */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete(RedisKeys.loginUser(userId));
            guard.markHealthy();
        } catch (Exception e) {
            if (pendingEvict.size() < MAX_PENDING_EVICT) {
                pendingEvict.add(userId);
            }
            guard.markDegraded("删除登录用户缓存");
            warnThrottled("删除登录用户缓存失败，已记入待补删 userId=" + userId, e);
        }
    }

    /**
     * 全局失效：递增权限版本号，让所有用户缓存下次请求即回源。
     *
     * <p>用于「一次变更影响一批用户」的场景：角色权限调整、菜单权限调整、
     * 角色下用户增删。相比按角色查一遍用户再逐个删 key，
     * 版本号只花一次 INCR，且不会因为漏查某个关联表而残留旧权限。</p>
     *
     * <p><b>失败时向上抛</b>：宁可让这次权限变更失败重试，
     * 也不能让管理员以为已收回权限、实际仍在生效。</p>
     */
    public void bumpPermissionVersion() {
        try {
            Long version = redis.opsForValue().increment(RedisKeys.PERM_VERSION);
            guard.markHealthy();
            log.info("权限版本号已更新为 {}，全量登录用户缓存于下次请求失效", version);
        } catch (Exception e) {
            guard.markDegraded("更新权限版本号");
            log.error("更新权限版本号失败，权限变更无法生效", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "权限变更生效失败，请检查缓存服务后重试");
        }
    }

    private void writeCache(String key, LoginUser user, String version) {
        try {
            CachedLoginUser snapshot = CachedLoginUser.of(user, version);
            String json = objectMapper.writeValueAsString(snapshot);
            long ttl = BASE_TTL.toSeconds() + ThreadLocalRandom.current().nextLong(JITTER_SECONDS + 1);
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            // 写缓存失败不影响本次请求，下次再回源即可
            warnThrottled("写入登录用户缓存失败 userId=" + user.getUserId(), e);
        }
    }

    /**
     * 补删 Redis 故障期间未能删除的缓存。
     *
     * <p>在每次读缓存前顺带执行，一旦 Redis 恢复就会自动清理干净，
     * 不需要人工介入。只要集合为空就是一次 boolean 判断，开销可忽略。</p>
     */
    private void flushPendingEvictions() {
        if (pendingEvict.isEmpty()) {
            return;
        }
        for (Long userId : pendingEvict) {
            try {
                redis.delete(RedisKeys.loginUser(userId));
                pendingEvict.remove(userId);
            } catch (Exception e) {
                // 仍然不可用，下次访问再试
                return;
            }
        }
        log.info("补删故障期间的登录用户缓存完成");
    }

    /** 日志限流：Redis 长时间不可用时避免每请求一条 warn */
    private void warnThrottled(String message, Exception e) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("{}（同类告警 {}s 内只打一次）", message, WARN_INTERVAL_MILLIS / 1000, e);
        }
    }
}
