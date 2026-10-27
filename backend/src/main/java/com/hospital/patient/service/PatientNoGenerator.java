package com.hospital.patient.service;

import com.hospital.patient.mapper.BizPatientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 患者编号生成（P0001、P0002…）。
 *
 * <p>用 Redis {@code INCR} 而不是「查 MAX + 1」：后者在并发建档时必然撞
 * {@code uk_patient_no}（两个请求读到同一个 MAX）。INCR 是原子的，天然不重复。</p>
 *
 * <p><b>懒初始化</b>：Redis 重启后计数会从 0 开始，若库里已有 P0001~P0012，
 * 再从 P0001 发就会撞唯一索引。所以第一次 INCR 得到 1 时，回查库里最大编号并
 * 把计数补齐。这一步只在空库/Redis 冷启动时发生，不影响正常路径性能。</p>
 *
 * <p><b>降级</b>：Redis 完全不可用时退回时间戳编号（可读性差但唯一），
 * 建档是核心业务，不能因为编号服务挂了就停摆。</p>
 */
@Slf4j
@Component
public class PatientNoGenerator {

    private static final String SEQ_KEY = "seq:patient:no";
    private static final String PREFIX = "P";

    private final StringRedisTemplate redis;
    private final BizPatientMapper patientMapper;

    public PatientNoGenerator(StringRedisTemplate redis, BizPatientMapper patientMapper) {
        this.redis = redis;
        this.patientMapper = patientMapper;
    }

    public String next() {
        try {
            Long seq = redis.opsForValue().increment(SEQ_KEY);
            if (seq == null) {
                return fallback();
            }
            if (seq == 1L) {
                // 冷启动：把库里已有的最大编号同步到计数上
                long max = parseSeq(patientMapper.selectMaxPatientNo());
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(SEQ_KEY, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return PREFIX + String.format("%04d", seq);
        } catch (Exception e) {
            log.warn("患者编号序列不可用，降级用时间戳生成", e);
            return fallback();
        }
    }

    private String fallback() {
        return PREFIX + System.currentTimeMillis();
    }

    private long parseSeq(String patientNo) {
        if (patientNo == null || patientNo.length() <= PREFIX.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(patientNo.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("库中存在非标准患者编号，已忽略 value={}", patientNo);
            return 0L;
        }
    }
}
