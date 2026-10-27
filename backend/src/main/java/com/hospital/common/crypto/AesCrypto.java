package com.hospital.common.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 字段加解密。
 *
 * <p><b>为什么是 GCM 而不是 CBC/ECB</b>：GCM 是带认证的加密（AEAD），
 * 密文被篡改后在 {@code doFinal} 就抛 {@code AEADBadTagException}，
 * 而不是解出一串乱码还当正常数据用。ECB 更不能用 —— 同样的明文永远得到
 * 同样的密文，攻击者不用解密就能看出「这两个患者身份证相同」。</p>
 *
 * <p><b>IV 必须每次随机并随密文一起存</b>：IV 不是密钥，可以公开，
 * 但重复使用同一个 IV 加密不同明文会直接泄露明文异或关系。
 * 密文格式 {@code Base64(12字节IV + 密文+认证标签)}。</p>
 */
@Slf4j
public final class AesCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public AesCrypto(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "AES-256 密钥必须是 32 字节，当前为 " + raw.length + " 字节（Base64 解码后）");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** 生成一个新的随机密钥（Base64），仅供运维生成配置值时使用 */
    public static String generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("生成 AES 密钥失败", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, out, IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("字段加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不足，可能被截断");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] body = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, body, 0, body.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败不能静默返回 null，否则上层会把「解不开」当成「这个字段没填」
            throw new IllegalStateException("字段解密失败，请检查密钥是否变更", e);
        }
    }
}
