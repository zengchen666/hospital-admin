package com.hospital.publichealth.service;

import com.hospital.publichealth.mapper.BizHealthRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 健康档案号生成（HR202610270001）。
 *
 * <p>与其它单号生成器同一套路：Redis INCR 保证并发唯一，
 * 首次 INCR=1 时回查库里最大号补齐，避免 Redis 重启后重号撞唯一索引。</p>
 */
@Slf4j
@Component
public class HealthRecordNoGenerator {

    private static final String PREFIX = "HR";
    private static final String SEQ_KEY_PREFIX = "seq:health:record:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizHealthRecordMapper recordMapper;

    public HealthRecordNoGenerator(StringRedisTemplate redis, BizHealthRecordMapper recordMapper) {
        this.redis = redis;
        this.recordMapper = recordMapper;
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
                long max = parseSeq(recordMapper.selectMaxRecordNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("档案号序列不可用，降级用时间戳生成", e);
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
