package com.hospital.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感字段哈希。
 *
 * <p>身份证查重完全依赖它：「同一个身份证必须算出同一个 hash」，
 * 否则重复建档就拦不住；同时「不同身份证不能撞 hash」，否则正常患者会被误判重复。</p>
 */
class HashUtilTest {

    @Test
    @DisplayName("同一身份证每次计算结果一致（查重的前提）")
    void hashIsDeterministic() {
        String first = HashUtil.sha256Hex("440300199005011234");
        String second = HashUtil.sha256Hex("440300199005011234");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    @DisplayName("首尾空格不影响结果（前端多输一个空格不该判成另一个人）")
    void hashTrimsWhitespace() {
        assertThat(HashUtil.sha256Hex("  440300199005011234  "))
                .isEqualTo(HashUtil.sha256Hex("440300199005011234"));
    }

    @Test
    @DisplayName("不同身份证得到不同 hash")
    void differentInputDifferentHash() {
        assertThat(HashUtil.sha256Hex("440300199005011234"))
                .isNotEqualTo(HashUtil.sha256Hex("440300199005011235"));
    }

    @Test
    @DisplayName("空值与空白返回 null，与库中 DEFAULT NULL 对齐")
    void hashOfBlankIsNull() {
        assertThat(HashUtil.sha256Hex(null)).isNull();
        assertThat(HashUtil.sha256Hex("")).isNull();
        assertThat(HashUtil.sha256Hex("   ")).isNull();
    }

    @Test
    @DisplayName("输出是标准 SHA-256 十六进制（64 位小写）")
    void hashFormatIsSha256Hex() {
        String hash = HashUtil.sha256Hex("abc");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        // 公开的 SHA-256 参考值，防止实现被悄悄换成 MD5
        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
