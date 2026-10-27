package com.hospital.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 哈希工具。
 *
 * <p>用于敏感字段的「可检索加密」：明文（如身份证）用 AES 加密存主字段，
 * 同时存一份 SHA-256 到 {@code *_hash} 列，查重与精确检索走 hash 列。
 * 这样既能检索、又不暴露明文。</p>
 *
 * <p><b>为什么用 SHA-256 而不是 MD5</b>：身份证号空间有限，MD5 已被撞穿；
 * SHA-256 至少让离线彩虹表的成本高一个量级。真要抗穷举还需加盐，
 * 但加盐后无法等值检索，属于业务权衡，本项目暂不加。</p>
 */
public final class HashUtil {

    private HashUtil() {
    }

    /** 计算 SHA-256 十六进制摘要；空值返回 null（与库中 DEFAULT NULL 对齐） */
    public static String sha256Hex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然内置 SHA-256，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
