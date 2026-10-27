package com.hospital.common.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化字段加密器。
 *
 * <p>密钥缺失或长度不对就<b>直接拒绝启动</b>：让它带着一个错误密钥跑起来，
 * 会在某次读库时才炸，而那时可能已经有一批数据用错误密钥加密写进去了 ——
 * 那是真正救不回来的事故。</p>
 */
@Slf4j
@Component
public class AesCryptoInitializer {

    private final AesProperties properties;

    public AesCryptoInitializer(AesProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String key = properties.getAesKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "缺少 hospital.crypto.aes-key 配置，生产环境请用环境变量 HOSPITAL_AES_KEY 注入");
        }
        AesCrypto crypto = new AesCrypto(key.trim());
        AesTypeHandler.register(crypto);
        log.info("字段加密器已就绪（AES-256-GCM）");
    }
}
