package com.hospital.pharmacy.service;

import com.hospital.pharmacy.mapper.BizPurchaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 采购单号生成（CG202610270001）。
 *
 * <p>与住院号同一套思路：Redis INCR 保证并发唯一，Redis 不可用时降级到
 * 「MAX(单号)+1」再降级到时间戳。单号重复会让两张采购单粘在一起，
 * 入库时根本分不清哪批货对应哪张单。</p>
 */
@Slf4j
@Component
public class PurchaseNoGenerator {

    private static final String PREFIX = "CG";
    private static final String SEQ_KEY_PREFIX = "seq:purchase:no:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizPurchaseMapper purchaseMapper;

    public PurchaseNoGenerator(StringRedisTemplate redis, BizPurchaseMapper purchaseMapper) {
        this.redis = redis;
        this.purchaseMapper = purchaseMapper;
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
                long max = parseSeq(purchaseMapper.selectMaxPurchaseNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("采购单号序列不可用，降级用时间戳生成", e);
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
            return 0L;
        }
    }
}
