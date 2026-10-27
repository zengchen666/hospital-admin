package com.hospital.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感信息脱敏。
 *
 * <p>脱敏是「列表里也不该看到完整身份证」的最后一道防线，
 * 一旦失效就是把患者隐私直接吐给前端。</p>
 */
class MaskUtilTest {

    @Test
    @DisplayName("手机号保留前 3 后 4")
    void maskPhone() {
        assertThat(MaskUtil.phone("13800001111")).isEqualTo("138****1111");
        assertThat(MaskUtil.phone("")).isEmpty();
        assertThat(MaskUtil.phone(null)).isNull();
    }

    @Test
    @DisplayName("过短的号码不崩溃，原样返回（宁可少脱敏也不能抛异常）")
    void maskPhoneHandlesShortInput() {
        assertThat(MaskUtil.phone("123")).isEqualTo("123");
        assertThat(MaskUtil.phone("123456")).isEqualTo("123456");
    }

    @Test
    @DisplayName("身份证保留前 4 后 4，中间全星号")
    void maskIdCard() {
        assertThat(MaskUtil.idCard("440300199005011234")).isEqualTo("4403**********1234");
        assertThat(MaskUtil.idCard("440300199005011234")).hasSize(18);
    }

    @Test
    @DisplayName("姓名两字打后一位、三字打中间")
    void maskName() {
        assertThat(MaskUtil.name("张三")).isEqualTo("张*");
        assertThat(MaskUtil.name("欧阳修")).isEqualTo("欧*修");
        assertThat(MaskUtil.name("王")).isEqualTo("王");
        assertThat(MaskUtil.name(null)).isNull();
    }

    @Test
    @DisplayName("地址只保留前 6 个字符")
    void maskAddress() {
        assertThat(MaskUtil.address("深圳市南山区科技园路 1 号")).startsWith("深圳市南山区");
        assertThat(MaskUtil.address("深圳市南山区科技园路 1 号")).endsWith("****");
        assertThat(MaskUtil.address("深圳")).isEqualTo("深圳");
    }
}
