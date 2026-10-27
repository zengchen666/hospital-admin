package com.hospital.common.security;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.config.HospitalProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p><b>为什么手写而不引 jjwt</b>：jjwt-jackson 依赖 Jackson 2，而 Boot 4 已切到 Jackson 3，
 * 两套同名包并存会带来排查成本；HS256 的签名/校验逻辑本身只有百来行，自己实现反而可控。</p>
 *
 * <p><b>安全要点（缺一不可）</b>：
 * <ol>
 *   <li>只接受 HS256：校验头部 alg，防「alg=none」绕过签名攻击</li>
 *   <li>签名比对用 {@link MessageDigest#isEqual} 常量时间比较，防时序侧信道</li>
 *   <li>必须校验 exp，且校验 iss 与本服务一致</li>
 *   <li>access 与 refresh 用 typ 字段区分，refresh 不能直接当 access 用</li>
 * </ol>
 *
 * <p><b>三个自定义声明</b>（P1-#7 引入，缺一个撤销能力就断一条）：
 * <ul>
 *   <li>{@code sid} 会话 ID：一次登录一个，access/refresh 共用。
 *       登出删掉会话，该设备的所有 refresh 立即失效；多设备各有 sid，互不影响。</li>
 *   <li>{@code jti} 令牌唯一 ID：refresh 每次轮换都换新，旧 jti 用过即废，防重放。</li>
 *   <li>{@code ver} 用户版本号：与库里的 {@code sys_user.version} 比对，
 *       改密/禁用后版本递增，已签发的 access token <b>立刻</b>失效。</li>
 * </ul>
 *
 * <p><b>兼容性提示</b>：{@code sid}/{@code ver} 是新增声明，本次上线前签发的 token
 * 解析后这两个字段为 null，会被鉴权链路判为无效 —— 即<b>升级后需重新登录一次</b>。
 * 这是安全机制变更的正常代价，宁可全员重登也不要保留无法撤销的 token。</p>
 */
@Component
public class JwtTokenProvider {

    private static final String ALGORITHM = "HS256";
    private static final String HMAC = "HmacSHA256";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final HospitalProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secretBytes;

    public JwtTokenProvider(HospitalProperties properties) {
        this.properties = properties;
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "hospital.jwt.secret 未配置。开发环境请在 application-dev.yml 中设置，"
                            + "生产环境请通过 JWT_SECRET 环境变量注入（至少 32 字节）。");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("hospital.jwt.secret 至少需要 32 字节（HS256 安全下限）");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public long getAccessTokenExpireSeconds() {
        return properties.getJwt().getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return properties.getJwt().getRefreshTokenExpireSeconds();
    }

    /** 签发结果：refresh 需要把 jti 登记到白名单，所以必须回传 */
    public record IssuedToken(String token, String jti) {
    }

    public IssuedToken createAccessToken(LoginUser user, String sessionId) {
        return create(user, TYPE_ACCESS, getAccessTokenExpireSeconds(), sessionId);
    }

    public IssuedToken createRefreshToken(LoginUser user, String sessionId) {
        return create(user, TYPE_REFRESH, getRefreshTokenExpireSeconds(), sessionId);
    }

    private IssuedToken create(LoginUser user, String type, long ttlSeconds, String sessionId) {
        long now = System.currentTimeMillis() / 1000;
        String jti = newJti();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", ALGORITHM);
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getUserId()));
        payload.put("username", user.getUsername());
        payload.put("deptId", user.getDeptId());
        payload.put("sid", sessionId);
        payload.put("jti", jti);
        payload.put("ver", user.getTokenVersion());
        payload.put("iss", properties.getJwt().getIssuer());
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        payload.put("typ", type);

        try {
            String encodedHeader = ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedPayload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = ENCODER.encodeToString(sign(signingInput));
            return new IssuedToken(signingInput + "." + signature, jti);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
    }

    /**
     * 校验并解析 token。
     *
     * @throws BusinessException 40101 表示 token 非法/过期，调用方按未登录处理
     */
    public TokenPayload parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public TokenPayload parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private TokenPayload parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = sign(signingInput);
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 常量时间比较，避免根据比较耗时反推签名
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Map<?, ?> header = readJson(parts[0]);
        if (!ALGORITHM.equals(header.get("alg"))) {
            // 防 alg 混淆/alg=none 攻击
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Map<?, ?> payload = readJson(parts[1]);
        String issuer = properties.getJwt().getIssuer();
        if (!issuer.equals(payload.get("iss"))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!expectedType.equals(payload.get("typ"))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        long exp = toLong(payload.get("exp"));
        if (exp <= System.currentTimeMillis() / 1000) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Object version = payload.get("ver");
        return new TokenPayload(
                toLong(payload.get("sub")),
                String.valueOf(payload.get("username")),
                payload.get("deptId") == null ? null : toLong(payload.get("deptId")),
                payload.get("sid") == null ? null : String.valueOf(payload.get("sid")),
                payload.get("jti") == null ? null : String.valueOf(payload.get("jti")),
                version == null ? null : (int) toLong(version),
                exp);
    }

    /** 生成令牌唯一 ID（去掉横线，减少 token 长度） */
    public static String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Map<?, ?> readJson(String base64Segment) {
        try {
            return objectMapper.readValue(DECODER.decode(base64Segment), Map.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secretBytes, HMAC));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * 解析后的载荷，字段刻意保持最小集，避免 token 过大。
     *
     * @param sessionId 会话 ID，登出时据此销毁会话
     * @param jti       令牌唯一 ID，refresh 轮换时据此作废旧令牌
     * @param version   用户版本号，与库里比对，改密/禁用后立即失效
     */
    public record TokenPayload(Long userId, String username, Long deptId,
                               String sessionId, String jti, Integer version, long expiresAt) {
    }
}
