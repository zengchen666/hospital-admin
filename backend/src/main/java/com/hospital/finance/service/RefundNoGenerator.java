package com.hospital.finance.service;

import com.hospital.finance.mapper.BizRefundMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 退费单号生成（TF202610260001）。
 */
@Slf4j
@Component
public class RefundNoGenerator {

    private static final String PREFIX = "TF";
    private static final String SEQ_KEY_PREFIX = "seq:refund:no:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizRefundMapper refundMapper;

    public RefundNoGenerator(StringRedisTemplate redis, BizRefundMapper refundMapper) {
        this.redis = redis;
        this.refundMapper = refundMapper;
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
                long max = parseSeq(refundMapper.selectMaxRefundNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("退费单号序列不可用，降级用时间戳生成", e);
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
            log.warn("库中存在非标准退费单号，已忽略 value={}", no);
            return 0L;
        }
    }
}
