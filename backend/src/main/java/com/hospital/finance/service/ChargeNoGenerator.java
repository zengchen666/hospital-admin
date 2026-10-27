package com.hospital.finance.service;

import com.hospital.finance.mapper.BizChargeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 收费单号生成（CHG202610260001）。
 */
@Slf4j
@Component
public class ChargeNoGenerator {

    private static final String PREFIX = "CHG";
    private static final String SEQ_KEY_PREFIX = "seq:charge:no:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizChargeMapper chargeMapper;

    public ChargeNoGenerator(StringRedisTemplate redis, BizChargeMapper chargeMapper) {
        this.redis = redis;
        this.chargeMapper = chargeMapper;
    }

    public String next() {
        String date = LocalDate.now().format(DATE_FMT);
        String prefix = PREFIX + date;
        String key = SEQ_KEY_PREFIX + date;
        try {
            Long seq = redis.opsForValue().increment(key);
            if (seq == null) {
                return fallback(prefix);
            }
            if (seq == 1L) {
                redis.expire(key, SEQ_TTL);
                long max = parseSeq(chargeMapper.selectMaxChargeNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("收费单号序列不可用，降级用时间戳生成", e);
            return fallback(prefix);
        }
    }

    private String fallback(String prefix) {
        return prefix + System.currentTimeMillis() % 1_000_000L;
    }

    private long parseSeq(String no, String prefix) {
        if (no == null || no.length() <= prefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(no.substring(prefix.length()));
        } catch (NumberFormatException e) {
            log.warn("库中存在非标准收费单号，已忽略 value={}", no);
            return 0L;
        }
    }
}
