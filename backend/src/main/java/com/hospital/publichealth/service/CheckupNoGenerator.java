package com.hospital.publichealth.service;

import com.hospital.publichealth.mapper.BizCheckupMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 体检单号生成（TJ202610270001）。
 */
@Slf4j
@Component
public class CheckupNoGenerator {

    private static final String PREFIX = "TJ";
    private static final String SEQ_KEY_PREFIX = "seq:health:checkup:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizCheckupMapper checkupMapper;

    public CheckupNoGenerator(StringRedisTemplate redis, BizCheckupMapper checkupMapper) {
        this.redis = redis;
        this.checkupMapper = checkupMapper;
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
                long max = parseSeq(checkupMapper.selectMaxCheckupNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("体检单号序列不可用，降级用时间戳生成", e);
            return fallback(prefix);
        }
    }

    private String fallback(String prefix) {
        return prefix + String.format("%04d", System.currentTimeMillis() % 10_000L);
    }

    private long parseSeq(String no, String prefix) {
        if (no == null || no.length() <= prefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(no.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
