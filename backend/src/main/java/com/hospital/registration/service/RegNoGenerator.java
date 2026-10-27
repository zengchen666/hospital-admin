package com.hospital.registration.service;

import com.hospital.registration.mapper.BizRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 挂号单号生成（REG202610260001）。
 *
 * <p>按天分段递增：单号里带日期，便于人工识别与按日对账；
 * Redis key 带日期并设 2 天过期，不会无限堆积。</p>
 *
 * <p>同样是 INCR 而不是查 MAX —— 挂号是并发最高的写操作之一，
 * 「先查后改」在早高峰必然重号。</p>
 */
@Slf4j
@Component
public class RegNoGenerator {

    private static final String REG_PREFIX = "REG";
    private static final String SEQ_KEY_PREFIX = "seq:reg:no:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 保留 2 天：跨天补打/退号重打都可能用到昨天的序号 */
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizRegistrationMapper registrationMapper;

    public RegNoGenerator(StringRedisTemplate redis, BizRegistrationMapper registrationMapper) {
        this.redis = redis;
        this.registrationMapper = registrationMapper;
    }

    public String next() {
        String date = LocalDate.now().format(DATE_FMT);
        String prefix = REG_PREFIX + date;
        String key = SEQ_KEY_PREFIX + date;
        try {
            Long seq = redis.opsForValue().increment(key);
            if (seq == null) {
                return fallback(prefix);
            }
            if (seq == 1L) {
                redis.expire(key, SEQ_TTL);
                long max = parseSeq(registrationMapper.selectMaxRegNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("挂号单号序列不可用，降级用时间戳生成", e);
            return fallback(prefix);
        }
    }

    private String fallback(String prefix) {
        return prefix + System.currentTimeMillis() % 1_000_000L;
    }

    private long parseSeq(String regNo, String prefix) {
        if (regNo == null || regNo.length() <= prefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(regNo.substring(prefix.length()));
        } catch (NumberFormatException e) {
            log.warn("库中存在非标准挂号单号，已忽略 value={}", regNo);
            return 0L;
        }
    }
}
