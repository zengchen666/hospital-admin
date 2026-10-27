package com.hospital.clinic.service;

import com.hospital.clinic.mapper.BizPrescriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 处方号生成（PRESC202610260001）。
 *
 * <p>与挂号单号同一套做法：按天分段 + Redis INCR，
 * 首次 INCR 得 1 时回查库里当天最大号补齐，避免 Redis 重启后重号撞唯一索引。</p>
 */
@Slf4j
@Component
public class PrescNoGenerator {

    private static final String PREFIX = "PRESC";
    private static final String SEQ_KEY_PREFIX = "seq:presc:no:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizPrescriptionMapper prescriptionMapper;

    public PrescNoGenerator(StringRedisTemplate redis, BizPrescriptionMapper prescriptionMapper) {
        this.redis = redis;
        this.prescriptionMapper = prescriptionMapper;
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
                long max = parseSeq(prescriptionMapper.selectMaxPrescNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("处方号序列不可用，降级用时间戳生成", e);
            return fallback(prefix);
        }
    }

    private String fallback(String prefix) {
        return prefix + System.currentTimeMillis() % 1_000_000L;
    }

    private long parseSeq(String prescNo, String prefix) {
        if (prescNo == null || prescNo.length() <= prefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(prescNo.substring(prefix.length()));
        } catch (NumberFormatException e) {
            log.warn("库中存在非标准处方号，已忽略 value={}", prescNo);
            return 0L;
        }
    }
}
