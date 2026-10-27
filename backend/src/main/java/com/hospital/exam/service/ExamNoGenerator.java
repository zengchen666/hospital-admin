package com.hospital.exam.service;

import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 检查申请单号（EXM…）与报告号（RPT…）生成。
 *
 * <p>两个序列共用同一套「Redis INCR + 懒初始化」逻辑，只是前缀与 key 不同。</p>
 */
@Slf4j
@Component
public class ExamNoGenerator {

    private static final String APPLY_PREFIX = "EXM";
    private static final String REPORT_PREFIX = "RPT";
    private static final String APPLY_SEQ_KEY = "seq:exam:apply:";
    private static final String REPORT_SEQ_KEY = "seq:exam:report:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration SEQ_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final BizExamApplyMapper applyMapper;
    private final BizExamReportMapper reportMapper;

    public ExamNoGenerator(StringRedisTemplate redis,
                           BizExamApplyMapper applyMapper,
                           BizExamReportMapper reportMapper) {
        this.redis = redis;
        this.applyMapper = applyMapper;
        this.reportMapper = reportMapper;
    }

    public String nextApplyNo() {
        String date = LocalDate.now().format(DATE_FMT);
        String prefix = APPLY_PREFIX + date;
        String key = APPLY_SEQ_KEY + date;
        try {
            Long seq = redis.opsForValue().increment(key);
            if (seq == null) {
                return fallback(prefix);
            }
            if (seq == 1L) {
                redis.expire(key, SEQ_TTL);
                long max = parseSeq(applyMapper.selectMaxApplyNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("检查申请单号序列不可用，降级用时间戳生成", e);
            return fallback(prefix);
        }
    }

    public String nextReportNo() {
        String date = LocalDate.now().format(DATE_FMT);
        String prefix = REPORT_PREFIX + date;
        String key = REPORT_SEQ_KEY + date;
        try {
            Long seq = redis.opsForValue().increment(key);
            if (seq == null) {
                return fallback(prefix);
            }
            if (seq == 1L) {
                redis.expire(key, SEQ_TTL);
                long max = parseSeq(reportMapper.selectMaxReportNo(prefix), prefix);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return prefix + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("报告号序列不可用，降级用时间戳生成", e);
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
            log.warn("库中存在非标准单号，已忽略 value={}", no);
            return 0L;
        }
    }
}
