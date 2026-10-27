package com.hospital.common.crypto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 字段加密配置。
 *
 * <p><b>密钥不落仓库</b>：本地用 yml 里的开发默认值，生产必须由环境变量
 * {@code HOSPITAL_AES_KEY} 注入。把密钥提交进 Git 等于把加密这件事本身
 * 变成摆设 —— 泄漏了代码就泄漏了所有患者身份证。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hospital.crypto")
public class AesProperties {

    /**
     * Base64 编码的 32 字节密钥（AES-256）。
     *
     * <p>启动时校验长度，配错了直接拒绝启动 —— 让它带着一个错误密钥跑起来，
     * 会在某次读库时才炸，那时候库里已经混进解不开的密文了。</p>
     */
    private String aesKey;
}
