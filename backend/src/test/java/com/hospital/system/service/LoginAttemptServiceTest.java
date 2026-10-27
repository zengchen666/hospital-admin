package com.hospital.system.service;

import com.hospital.common.cache.RedisKeys;
import com.hospital.common.exception.BusinessException;
import com.hospital.config.HospitalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败锁定。
 *
 * <p>锁定的价值在于「挡住暴力破解」，所以用例重点验证：
 * 阈值是否精确、锁定时长是否可读、账号名变形能否绕过、Redis 故障时是否 fail-open。</p>
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String USERNAME = "admin";
    private static final String KEY = RedisKeys.loginFail(USERNAME);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        HospitalProperties properties = new HospitalProperties();
        properties.getJwt().setLoginFailLimit(5);
        properties.getJwt().setLoginLockMinutes(30);
        service = new LoginAttemptService(redis, new com.hospital.common.cache.RedisGuard(), properties);
    }

    @Test
    @DisplayName("失败次数未达阈值时允许继续尝试")
    void belowThresholdIsAllowed() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("4");

        assertThatCode(() -> service.assertNotLocked(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("失败次数达到阈值即锁定，提示剩余分钟数")
    void reachingThresholdLocks() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("5");
        when(redis.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(1800L);

        assertThatThrownBy(() -> service.assertNotLocked(USERNAME))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("30 分钟");
    }

    @Test
    @DisplayName("剩余锁定时间不足一分钟时向上取整为 1 分钟，不显示 0")
    void remainingMinutesRoundsUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("5");
        when(redis.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(20L);

        assertThatThrownBy(() -> service.assertNotLocked(USERNAME))
                .hasMessageContaining("1 分钟");
    }

    @Test
    @DisplayName("没有失败记录时直接放行，不查 TTL")
    void noRecordMeansPass() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(null);

        assertThatCode(() -> service.assertNotLocked(USERNAME)).doesNotThrowAnyException();
        verify(redis, never()).getExpire(anyString(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("首次失败设置计数窗口（否则计数永不过期，等于永久锁定）")
    void firstFailureSetsTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(1L);

        service.recordFailure(USERNAME);

        verify(redis).expire(eq(KEY), any(Duration.class));
    }

    @Test
    @DisplayName("达到阈值时把锁定时长重置为完整周期，从触发那一刻起算")
    void thresholdResetsTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(5L);

        service.recordFailure(USERNAME);

        verify(redis).expire(eq(KEY), any(Duration.class));
    }

    @Test
    @DisplayName("中间失败不刷新 TTL，避免攻击者把合法用户永久锁死")
    void intermediateFailureDoesNotExtendTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(3L);

        service.recordFailure(USERNAME);

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("登录成功后清零计数")
    void resetClearsCounter() {
        service.reset(USERNAME);

        verify(redis).delete(KEY);
    }

    @Test
    @DisplayName("账号名大小写与空格不影响计数，否则换个写法就能绕过限制")
    void usernameIsNormalized() {
        service.reset("  ADMIN  ");

        verify(redis).delete(KEY);
    }

    @Test
    @DisplayName("Redis 故障时 fail-open：不能因为缓存挂了把所有人挡在门外")
    void redisFailureFailsOpen() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> service.assertNotLocked(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("写入失败不影响登录主流程")
    void recordFailureSwallowsRedisError() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> service.recordFailure(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("计数被写成脏数据时按 0 处理，不抛解析异常")
    void dirtyCounterValueIsTolerated() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("not-a-number");

        assertThatCode(() -> service.assertNotLocked(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("熔断期内不再访问 Redis")
    void circuitBreakerSkipsRedis() {
        com.hospital.common.cache.RedisGuard guard = new com.hospital.common.cache.RedisGuard();
        HospitalProperties properties = new HospitalProperties();
        LoginAttemptService broken = new LoginAttemptService(redis, guard, properties);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        broken.assertNotLocked(USERNAME);
        broken.recordFailure(USERNAME);

        // 失败一次后就熔断，后续调用不再触发 Redis 命令
        verify(valueOps, times(1)).get(anyString());
    }
}
