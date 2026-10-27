package com.hospital.common.security;

import com.hospital.common.exception.BusinessException;
import com.hospital.config.HospitalProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与校验的回归测试。
 *
 * <p>这些用例是安全基线：任何一条变红都意味着 token 可以被伪造/越权使用，
 * 不允许「先跳过、以后再说」。</p>
 */
class JwtTokenProviderTest {

    private static final String SECRET = "hospital-dev-only-secret-key-please-change-in-prod-2026";
    private static final String OTHER_SECRET = "another-secret-key-at-least-32-bytes-long-000000";

    private JwtTokenProvider provider() {
        return provider(SECRET);
    }

    private JwtTokenProvider provider(String secret) {
        HospitalProperties properties = new HospitalProperties();
        properties.getJwt().setSecret(secret);
        return new JwtTokenProvider(properties);
    }

    private LoginUser user() {
        return LoginUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .deptId(1001L)
                .tokenVersion(3)
                .roleCodes(new LinkedHashSet<>(Set.of("admin")))
                .permissions(new LinkedHashSet<>(Set.of("system:user:query")))
                .build();
    }

    @Test
    @DisplayName("签发的 access token 能解析出全部关键声明")
    void accessTokenCarriesAllClaims() {
        JwtTokenProvider provider = provider();
        JwtTokenProvider.IssuedToken issued = provider.createAccessToken(user(), "session-abc");

        JwtTokenProvider.TokenPayload payload = provider.parseAccessToken(issued.token());

        assertThat(payload.userId()).isEqualTo(1L);
        assertThat(payload.username()).isEqualTo("admin");
        assertThat(payload.deptId()).isEqualTo(1001L);
        assertThat(payload.sessionId()).isEqualTo("session-abc");
        assertThat(payload.jti()).isEqualTo(issued.jti());
        // ver 是「改密即踢下线」的依据，必须原样带回来
        assertThat(payload.version()).isEqualTo(3);
        assertThat(payload.expiresAt()).isGreaterThan(System.currentTimeMillis() / 1000);
    }

    @Test
    @DisplayName("refresh token 不能当 access token 用")
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtTokenProvider provider = provider();
        String refresh = provider.createRefreshToken(user(), "sid").token();

        assertThatThrownBy(() -> provider.parseAccessToken(refresh))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("access token 不能当 refresh token 用")
    void accessTokenCannotBeUsedAsRefreshToken() {
        JwtTokenProvider provider = provider();
        String access = provider.createAccessToken(user(), "sid").token();

        assertThatThrownBy(() -> provider.parseRefreshToken(access))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("签名被篡改的 token 必须拒绝")
    void tamperedSignatureIsRejected() {
        JwtTokenProvider provider = provider();
        String token = provider.createAccessToken(user(), "sid").token();
        String[] parts = token.split("\\.");

        // 注意：不能改最后一个字符。base64url 末位的低位是填充位，
        // 改了可能解码出完全相同的字节，签名照样通过（实测踩过）。
        // 改首字符的每个 bit 都有效，必然改变签名。
        char[] signature = parts[2].toCharArray();
        signature[0] = signature[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);

        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("payload 被篡改（提权）必须拒绝")
    void tamperedPayloadIsRejected() {
        JwtTokenProvider provider = provider();
        String token = provider.createAccessToken(user(), "sid").token();
        String[] parts = token.split("\\.");
        // 把 sub 从 1 改成 999，签名不变
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999\",\"typ\":\"access\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> provider.parseAccessToken(forged))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("alg=none 的无签名 token 必须拒绝（经典绕过攻击）")
    void algNoneAttackIsRejected() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                "{\"sub\":\"1\",\"typ\":\"access\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));

        // 攻击形态 1：签名段留空
        assertThatThrownBy(() -> provider().parseAccessToken(header + "." + payload + "."))
                .isInstanceOf(BusinessException.class);
        // 攻击形态 2：签名段随便填
        assertThatThrownBy(() -> provider().parseAccessToken(header + "." + payload + ".AAAA"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("换一个密钥签发的 token 必须拒绝")
    void tokenFromOtherSecretIsRejected() {
        String foreign = provider(OTHER_SECRET).createAccessToken(user(), "sid").token();

        assertThatThrownBy(() -> provider().parseAccessToken(foreign))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("已过期的 token 必须拒绝")
    void expiredTokenIsRejected() {
        HospitalProperties properties = new HospitalProperties();
        properties.getJwt().setSecret(SECRET);
        // 负数 TTL：签发出来就已经过期
        properties.getJwt().setAccessTokenExpireSeconds(-10L);
        JwtTokenProvider provider = new JwtTokenProvider(properties);

        String token = provider.createAccessToken(user(), "sid").token();

        assertThatThrownBy(() -> provider.parseAccessToken(token))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("格式非法的 token 必须拒绝而不是抛系统异常")
    void malformedTokenIsRejected() {
        JwtTokenProvider provider = provider();

        for (String bad : new String[]{null, "", "abc", "a.b", "a.b.c.d", "..", "a.b.c"}) {
            assertThatThrownBy(() -> provider.parseAccessToken(bad))
                    .as("输入=%s", bad)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("每次签发的 jti 都不相同（轮换与防重放的前提）")
    void jtiIsUniquePerIssue() {
        JwtTokenProvider provider = provider();

        String first = provider.createRefreshToken(user(), "sid").jti();
        String second = provider.createRefreshToken(user(), "sid").jti();

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    @Test
    @DisplayName("密钥缺失或过短时启动即失败（fail-fast，不能带着弱密钥上线）")
    void weakOrMissingSecretFailsFast() {
        HospitalProperties blank = new HospitalProperties();
        blank.getJwt().setSecret("   ");
        assertThatThrownBy(() -> new JwtTokenProvider(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");

        HospitalProperties tooShort = new HospitalProperties();
        tooShort.getJwt().setSecret("short-secret");
        assertThatThrownBy(() -> new JwtTokenProvider(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("access 与 refresh 的有效期分别取自配置")
    void tokenTtlComesFromConfiguration() {
        JwtTokenProvider provider = provider();
        long now = System.currentTimeMillis() / 1000;

        long accessExp = provider.parseAccessToken(provider.createAccessToken(user(), "sid").token()).expiresAt();
        long refreshExp = provider.parseRefreshToken(provider.createRefreshToken(user(), "sid").token()).expiresAt();

        assertThat(accessExp - now).isBetween(7000L, 7210L);
        assertThat(refreshExp - now).isBetween(604700L, 604810L);
    }
}
