package com.hospital.system.service;

import com.hospital.common.cache.RedisGuard;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话与 refresh 令牌白名单。
 *
 * <p>这是「令牌可撤销」的落点，验证重点：
 * 会话与令牌是否真的写进去了、消费是否一次性、Redis 故障时是否 fail-closed。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    private static final long REFRESH_TTL_SECONDS = 604800L;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        HospitalProperties properties = new HospitalProperties();
        service = new AuthSessionService(redis, new RedisGuard(), properties);
    }

    @Test
    @DisplayName("新建会话写入 Redis，TTL 与 refresh 有效期对齐")
    void createSessionWritesWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String sid = service.createSession(1L);

        assertThat(sid).isNotBlank();
        verify(valueOps).set(eq(RedisKeys.session(sid)), eq("1"),
                eq(Duration.ofSeconds(REFRESH_TTL_SECONDS)));
    }

    @Test
    @DisplayName("每次登录的会话 ID 都不同（多设备互不影响的前提）")
    void sessionIdsAreUnique() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String first = service.createSession(1L);
        String second = service.createSession(1L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("写会话失败时登录直接失败（fail-closed，不能发出无法撤销的令牌）")
    void createSessionFailsClosed() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // set(key, value, Duration) 返回 void，不能用 when() 打桩
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.createSession(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录服务");
    }

    @Test
    @DisplayName("会话 ID 为空时直接判定无效，不浪费一次查询")
    void blankSessionIsInactive() {
        assertThat(service.isSessionActive(null)).isFalse();
        assertThat(service.isSessionActive("  ")).isFalse();

        verify(redis, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("会话存在时判定有效")
    void existingSessionIsActive() {
        when(redis.hasKey("auth:session:sid-1")).thenReturn(true);

        assertThat(service.isSessionActive("sid-1")).isTrue();
    }

    @Test
    @DisplayName("登出后会话不存在，判定无效")
    void destroyedSessionIsInactive() {
        when(redis.hasKey("auth:session:sid-1")).thenReturn(false);

        assertThat(service.isSessionActive("sid-1")).isFalse();
    }

    @Test
    @DisplayName("Redis 故障时判定无效（fail-closed：宁可让用户重登，也不放行无法校验的令牌）")
    void sessionCheckFailsClosedOnRedisError() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isSessionActive("sid-1")).isFalse();
    }

    @Test
    @DisplayName("登记 refresh 令牌失败时拒绝签发（否则会发出无法轮换的令牌）")
    void registerRefreshFailsClosed() {
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.registerRefreshToken("jti-1", "sid-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("消费 refresh 令牌：DEL 成功即表示首次使用")
    void consumeReturnsTrueOnFirstUse() {
        when(redis.delete(RedisKeys.refreshJti("jti-1"))).thenReturn(true);

        assertThat(service.consumeRefreshToken("jti-1")).isTrue();
    }

    @Test
    @DisplayName("同一个 refresh 令牌第二次消费必须失败（防重放）")
    void consumeReturnsFalseOnReplay() {
        when(redis.delete(RedisKeys.refreshJti("jti-1"))).thenReturn(false);

        assertThat(service.consumeRefreshToken("jti-1")).isFalse();
    }

    @Test
    @DisplayName("jti 为空时判定为已使用，不放行")
    void consumeWithoutJtiIsRejected() {
        assertThat(service.consumeRefreshToken(null)).isFalse();
        assertThat(service.consumeRefreshToken("")).isFalse();

        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("消费时 Redis 故障判定为失败，刷新被拒")
    void consumeFailsClosedOnRedisError() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.consumeRefreshToken("jti-1")).isFalse();
    }

    @Test
    @DisplayName("销毁会话失败不抛异常（TTL 到期兜底，登出流程不该被卡住）")
    void destroySessionSwallowsError() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> service.destroySession("sid-1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("会话 ID 为空时销毁操作直接跳过")
    void destroyBlankSessionIsNoop() {
        service.destroySession(null);
        service.destroySession("");

        verify(redis, never()).delete(anyString());
    }
}
