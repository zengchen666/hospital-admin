package com.hospital.system.service;

import com.hospital.common.cache.RedisGuard;
import com.hospital.common.cache.RedisKeys;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.CachedLoginUser;
import com.hospital.common.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限缓存的一致性、降级与熔断行为。
 *
 * <p>重点不是「能不能缓存」，而是<b>缓存出问题时权限是否还准确</b>：
 * 版本号变更必须回源、Redis 挂掉必须降级而不是报错、熔断后不能每次都等超时。</p>
 */
@ExtendWith(MockitoExtension.class)
class LoginUserCacheServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CACHE_KEY = RedisKeys.loginUser(USER_ID);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisGuard guard = new RedisGuard();
    private LoginUserCacheService service;

    @BeforeEach
    void setUp() {
        service = new LoginUserCacheService(redis, objectMapper, guard);
    }

    private LoginUser user(long id, int tokenVersion) {
        return LoginUser.builder()
                .userId(id)
                .username("admin")
                .realName("系统管理员")
                .deptId(1001L)
                .tokenVersion(tokenVersion)
                .roleCodes(new LinkedHashSet<>(Set.of("admin")))
                .permissions(new LinkedHashSet<>(Set.of("system:user:query")))
                .build();
    }

    private String cacheJson(LoginUser user, String permVersion) throws Exception {
        return objectMapper.writeValueAsString(CachedLoginUser.of(user, permVersion));
    }

    @Test
    @DisplayName("缓存命中时不查库")
    void cacheHitDoesNotTouchDatabase() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(RedisKeys.PERM_VERSION)).thenReturn("5");
        when(valueOps.get(CACHE_KEY)).thenReturn(cacheJson(user(USER_ID, 0), "5"));
        AtomicInteger dbCalls = new AtomicInteger();

        LoginUser result = service.get(USER_ID, id -> {
            dbCalls.incrementAndGet();
            return user(id, 0);
        });

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(dbCalls.get()).isZero();
    }

    @Test
    @DisplayName("缓存未命中时回源并回填，TTL 带抖动")
    void cacheMissLoadsFromDbAndBackfills() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(RedisKeys.PERM_VERSION)).thenReturn("5");
        when(valueOps.get(CACHE_KEY)).thenReturn(null);

        LoginUser result = service.get(USER_ID, id -> user(id, 0));

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        verify(valueOps).set(eq(CACHE_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("全局权限版本变化后缓存作废，必须回源")
    void versionMismatchForcesReload() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        // 缓存里是旧版本 4，当前已是 5 —— 说明期间发生过角色/菜单调整
        when(valueOps.get(RedisKeys.PERM_VERSION)).thenReturn("5");
        when(valueOps.get(CACHE_KEY)).thenReturn(cacheJson(user(USER_ID, 0), "4"));
        AtomicInteger dbCalls = new AtomicInteger();

        service.get(USER_ID, id -> {
            dbCalls.incrementAndGet();
            return user(id, 0);
        });

        assertThat(dbCalls.get()).isEqualTo(1);
        // 回填时必须写入新版本号，否则下次还要回源
        verify(valueOps).set(eq(CACHE_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 读失败时降级查库，不能把异常抛给调用方")
    void redisFailureFallsBackToDb() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        LoginUser result = service.get(USER_ID, id -> user(id, 0));

        assertThat(result.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("熔断期内不再访问 Redis，直接查库（避免每请求等满超时）")
    void circuitBreakerSkipsRedis() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        // 第一次：探测失败 → 进入熔断
        service.get(USER_ID, id -> user(id, 0));
        // 第二次：应直接返回，不再碰 Redis
        service.get(USER_ID, id -> user(id, 0));

        // 只有第一次真正读了 Redis（第一行 get 就抛异常，后续命令不再发出）
        verify(valueOps, times(1)).get(anyString());
    }

    @Test
    @DisplayName("Redis 恢复后熔断自动解除，缓存重新生效")
    void circuitBreakerRecovers() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        service.get(USER_ID, id -> user(id, 0));

        guard.markHealthy();
        // 用 doReturn：when(mock.method()) 会真的调用一次，
        // 而此刻 stub 还是「抛异常」，直接写 when(...) 会把异常抛在测试代码里
        doReturn(null).when(valueOps).get(anyString());
        service.get(USER_ID, id -> user(id, 0));

        // 失败时 1 次，恢复后 2 次（PERM_VERSION + CACHE_KEY）
        verify(valueOps, times(3)).get(anyString());
    }

    @Test
    @DisplayName("删除缓存失败会记入待补删，Redis 恢复后自动清掉")
    void failedEvictIsCompensatedLater() {
        // 第一次删除抛错，第二次成功
        when(redis.delete(CACHE_KEY)).thenThrow(new RuntimeException("redis down")).thenReturn(true);

        service.evict(USER_ID);
        // 熔断由 evict 失败触发，手动恢复模拟 Redis 已回来
        guard.markHealthy();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        service.get(USER_ID, id -> user(id, 0));

        // 一次失败 + 一次补删
        verify(redis, times(2)).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("删除缓存成功时不会进入待补删队列")
    void successfulEvictSkipsCompensation() {
        when(redis.delete(CACHE_KEY)).thenReturn(true);

        service.evict(USER_ID);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        service.get(USER_ID, id -> user(id, 0));

        // 正常删除后，get 不应再补删一次
        verify(redis, times(1)).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("递增权限版本失败必须抛错（fail-closed：宁可改失败，不能改了却没生效）")
    void bumpVersionFailsClosed() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(RedisKeys.PERM_VERSION)).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.bumpPermissionVersion())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缓存服务");
    }

    @Test
    @DisplayName("userId 为 null 时 evict 直接返回，不访问 Redis")
    void evictWithNullUserIdIsNoop() {
        service.evict(null);

        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("缓存快照里绝不能出现密码（bcrypt 哈希也不该扩散到缓存层）")
    void cachedSnapshotNeverContainsPassword() throws Exception {
        LoginUser withPassword = LoginUser.builder()
                .userId(USER_ID)
                .username("admin")
                .password("{bcrypt}$2a$10$abcdefghijklmnopqrstuv")
                .tokenVersion(0)
                .build();

        String json = objectMapper.writeValueAsString(CachedLoginUser.of(withPassword, "1"));

        assertThat(json).doesNotContain("password").doesNotContain("bcrypt").doesNotContain("$2a$");
        // 但鉴权需要的字段必须都在
        assertThat(json).contains("admin").contains("permissions").contains("permVersion");
    }
}
