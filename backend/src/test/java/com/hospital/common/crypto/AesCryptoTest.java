package com.hospital.common.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES-256-GCM 字段加解密。
 *
 * <p>这里最该验证的是<b>「同样的明文两次加密结果不同」</b>：
 * 如果两次加密得到同一个密文，说明 IV 被复用了，ECB 模式的那些老问题
 * （同样的身份证在库里长一个样，不用解密就能看出谁和谁相同）就又回来了。</p>
 */
class AesCryptoTest {

    private static AesCrypto crypto;

    @BeforeAll
    static void setUp() {
        crypto = new AesCrypto(AesCrypto.generateKey());
    }

    @Test
    @DisplayName("加解密往返一致")
    void encryptThenDecrypt() {
        String plain = "440301199001011234";

        String cipher = crypto.encrypt(plain);

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(crypto.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("同一明文两次加密结果不同（IV 随机，不能复用）")
    void samePlainYieldsDifferentCipher() {
        String plain = "13800138000";

        String first = crypto.encrypt(plain);
        String second = crypto.encrypt(plain);

        assertThat(first).isNotEqualTo(second);
        // 但都能解回同一个明文
        assertThat(crypto.decrypt(first)).isEqualTo(plain);
        assertThat(crypto.decrypt(second)).isEqualTo(plain);
    }

    @Test
    @DisplayName("null 加密仍是 null，不产生密文垃圾")
    void nullStaysNull() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.decrypt("")).isNull();
    }

    @Test
    @DisplayName("中文与超长内容都能正确往返")
    void unicodeAndLongText() {
        String chinese = "广东省深圳市南山区科技园路 1 号";
        assertThat(crypto.decrypt(crypto.encrypt(chinese))).isEqualTo(chinese);

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longText.append("病历").append(i).append("；");
        }
        assertThat(crypto.decrypt(crypto.encrypt(longText.toString()))).isEqualTo(longText.toString());
    }

    @Test
    @DisplayName("密文长度可预估，varchar(255) 装得下身份证密文")
    void cipherFitsInColumn() {
        // 12 字节 IV + 18 字节明文 + 16 字节认证标签 = 46 字节 → Base64 约 64 字符
        String cipher = crypto.encrypt("440301199001011234");
        assertThat(cipher.length()).isLessThan(255);
        assertThat(cipher.length()).isGreaterThan(40);
    }

    @Test
    @DisplayName("篡改密文会被 GCM 认证标签发现，而不是解出乱码")
    void tamperedCipherIsRejected() {
        String cipher = crypto.encrypt("440301199001011234");
        byte[] bytes = Base64.getDecoder().decode(cipher);
        // 改最后一个字节（认证标签区）
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @DisplayName("截断的密文报错而不是静默返回 null")
    void truncatedCipherIsRejected() {
        String shortCipher = Base64.getEncoder().encodeToString(new byte[8]);

        assertThatThrownBy(() -> crypto.decrypt(shortCipher))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("换密钥后解不开，必须报出来而不是当空值")
    void wrongKeyCannotDecrypt() {
        AesCrypto other = new AesCrypto(AesCrypto.generateKey());
        String cipher = crypto.encrypt("440301199001011234");

        assertThatThrownBy(() -> other.decrypt(cipher))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("密钥长度不是 32 字节时拒绝构造")
    void invalidKeyLengthRejected() {
        String key16 = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesCrypto(key16))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须是 32 字节");
    }

    @Test
    @DisplayName("生成的密钥是合法 32 字节 Base64")
    void generatedKeyIsValid() {
        String key = AesCrypto.generateKey();

        assertThat(Base64.getDecoder().decode(key)).hasSize(32);
        // 每次生成的都不一样
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            keys.add(AesCrypto.generateKey());
        }
        assertThat(keys).hasSize(5);
    }

    @Test
    @DisplayName("密文是标准 Base64，可直接落 varchar")
    void cipherIsBase64() {
        String cipher = crypto.encrypt("13800138000");

        byte[] decoded = Base64.getDecoder().decode(cipher);
        // IV 12 字节 + 明文 11 字节 + 标签 16 字节
        assertThat(decoded).hasSize(12 + 11 + 16);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).doesNotContain("13800138000");
    }
}
