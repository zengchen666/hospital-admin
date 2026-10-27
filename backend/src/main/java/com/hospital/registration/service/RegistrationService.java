package com.hospital.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.dto.RegistrationQuery;
import com.hospital.registration.dto.RegistrationRequest;
import com.hospital.registration.dto.RegistrationResult;
import com.hospital.registration.dto.RegistrationVO;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.entity.BizSchedule;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.registration.mapper.BizScheduleMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 挂号服务：挂号、退号、候诊队列、叫号。
 *
 * <p><b>防超卖是这里的第一要务</b>。扣号源走条件更新
 * （{@code remain_num = remain_num - 1 WHERE remain_num > 0}），
 * 影响行数为 0 就直接返回 40901，绝不「先查后改」。</p>
 *
 * <p><b>退号也必须幂等</b>：用「状态条件更新」把 0/1 → 3，
 * 只有真正改成功的那一次才回补号源，避免并发双退把号源补多。</p>
 */
@Slf4j
@Service
public class RegistrationService {

    private static final int STATUS_WAITING = 0;
    private static final int STATUS_IN_VISIT = 1;
    private static final int STATUS_FINISHED = 2;
    private static final int STATUS_CANCELLED = 3;

    /** 排队号序列 key 前缀，按排班维度隔离 */
    private static final String QUEUE_SEQ_PREFIX = "seq:queue:";
    /** 排队号序列保留 7 天，覆盖排班周期，之后随 TTL 自动清理 */
    private static final Duration QUEUE_SEQ_TTL = Duration.ofDays(7);

    private final BizRegistrationMapper registrationMapper;
    private final BizScheduleMapper scheduleMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final RegNoGenerator regNoGenerator;
    private final StringRedisTemplate redis;

    public RegistrationService(BizRegistrationMapper registrationMapper,
                               BizScheduleMapper scheduleMapper,
                               BizPatientMapper patientMapper,
                               SysUserMapper userMapper,
                               RegNoGenerator regNoGenerator,
                               StringRedisTemplate redis) {
        this.registrationMapper = registrationMapper;
        this.scheduleMapper = scheduleMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.regNoGenerator = regNoGenerator;
        this.redis = redis;
    }

    /**
     * 生成排队号。
     *
     * <p><b>必须走 Redis INCR</b>：一开始用 {@code MAX(queue_no) + 1}，
     * 并发抢号时实测出现 {@code 1,2,2} 的重复号 —— 两个请求同时读到同一个 MAX。
     * 排队号重复会让叫号屏叫错人，属于必须修掉的问题。</p>
     *
     * <p>降级：Redis 不可用时退回 MAX+1（可能重复但不影响挂号成功），
     * 记 warn 便于事后核对。</p>
     */
    private int nextQueueNo(Long scheduleId) {
        String key = QUEUE_SEQ_PREFIX + scheduleId;
        try {
            Long seq = redis.opsForValue().increment(key);
            if (seq == null) {
                return registrationMapper.selectMaxQueueNo(scheduleId) + 1;
            }
            if (seq == 1L) {
                redis.expire(key, QUEUE_SEQ_TTL);
                int max = registrationMapper.selectMaxQueueNo(scheduleId);
                if (max > 0) {
                    Long synced = redis.opsForValue().increment(key, max);
                    seq = synced == null ? max + 1 : synced;
                }
            }
            return seq.intValue();
        } catch (Exception e) {
            log.warn("排队号序列不可用，降级用 MAX+1（可能重复）scheduleId={}", scheduleId, e);
            return registrationMapper.selectMaxQueueNo(scheduleId) + 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RegistrationResult register(RegistrationRequest request) {
        BizPatient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在，请先建档");
        }

        BizSchedule schedule = scheduleMapper.selectById(request.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "排班不存在");
        }
        if (!Integer.valueOf(1).equals(schedule.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该排班已停诊");
        }
        if (schedule.getScheduleDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该排班日期已过，无法挂号");
        }
        if (registrationMapper.countActiveByScheduleAndPatient(schedule.getId(), patient.getId()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该患者在本时段已有有效挂号，请勿重复挂号");
        }

        // 关键：条件更新扣号源，失败即号源不足（并发下由数据库行锁保证不超卖）
        if (scheduleMapper.decreaseStock(schedule.getId()) == 0) {
            throw new BusinessException(ErrorCode.NO_STOCK);
        }

        Integer regType = request.getRegType() == null ? schedule.getRegType() : request.getRegType();
        BizRegistration entity = new BizRegistration();
        entity.setRegNo(regNoGenerator.next());
        entity.setPatientId(patient.getId());
        entity.setDoctorId(schedule.getDoctorId());
        entity.setDeptId(schedule.getDeptId());
        entity.setScheduleId(schedule.getId());
        entity.setRegType(regType);
        // 挂号费取排班上的价格，前端传的一律忽略，防止改价
        entity.setRegFee(schedule.getRegFee());
        entity.setQueueNo(nextQueueNo(schedule.getId()));
        entity.setStatus(STATUS_WAITING);
        entity.setRegTime(LocalDateTime.now());

        // 插入失败会连同上面的扣减一起回滚，不会出现「号扣了单没出」
        registrationMapper.insert(entity);

        log.info("挂号成功 regNo={} patientId={} scheduleId={} queueNo={}",
                entity.getRegNo(), patient.getId(), schedule.getId(), entity.getQueueNo());

        return RegistrationResult.builder()
                .id(String.valueOf(entity.getId()))
                .regNo(entity.getRegNo())
                .queueNo(entity.getQueueNo())
                .regFee(entity.getRegFee())
                .status(entity.getStatus())
                .build();
    }

    /**
     * 退号：状态条件更新 + 回补号源。
     *
     * <p>只有「0/1 → 3」这一步真的成功了才补号源，
     * 因此重复点击、并发退号都只会补一次。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        BizRegistration registration = registrationMapper.selectById(id);
        if (registration == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "挂号记录不存在");
        }
        if (Objects.equals(registration.getStatus(), STATUS_CANCELLED)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该挂号已退号，请勿重复操作");
        }
        if (Objects.equals(registration.getStatus(), STATUS_FINISHED)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已完成的挂号不能退号");
        }
        if (Objects.equals(registration.getStatus(), STATUS_IN_VISIT)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "就诊中的挂号需医生结束接诊后才能退号");
        }

        int affected = registrationMapper.update(null, Wrappers.<BizRegistration>lambdaUpdate()
                .eq(BizRegistration::getId, id)
                .eq(BizRegistration::getStatus, STATUS_WAITING)
                .set(BizRegistration::getStatus, STATUS_CANCELLED)
                .set(BizRegistration::getUpdateTime, LocalDateTime.now()));
        if (affected == 0) {
            // 并发下另一个请求已经退掉了
            throw new BusinessException(ErrorCode.CONFLICT, "挂号状态已变化，请刷新后重试");
        }

        scheduleMapper.increaseStock(registration.getScheduleId());
        log.info("退号成功 regNo={} scheduleId={}", registration.getRegNo(), registration.getScheduleId());
    }

    /** 叫号：待就诊 → 就诊中 */
    @Transactional(rollbackFor = Exception.class)
    public void call(Long id) {
        int affected = registrationMapper.update(null, Wrappers.<BizRegistration>lambdaUpdate()
                .eq(BizRegistration::getId, id)
                .eq(BizRegistration::getStatus, STATUS_WAITING)
                .set(BizRegistration::getStatus, STATUS_IN_VISIT)
                .set(BizRegistration::getUpdateTime, LocalDateTime.now()));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "只有待就诊的挂号可以叫号");
        }
    }

    public PageResult<RegistrationVO> page(RegistrationQuery query) {
        LambdaQueryWrapper<BizRegistration> wrapper = Wrappers.<BizRegistration>lambdaQuery()
                .eq(query.getPatientId() != null, BizRegistration::getPatientId, query.getPatientId())
                .eq(query.getDoctorId() != null, BizRegistration::getDoctorId, query.getDoctorId())
                .eq(query.getDeptId() != null, BizRegistration::getDeptId, query.getDeptId())
                .eq(query.getScheduleId() != null, BizRegistration::getScheduleId, query.getScheduleId())
                .eq(query.getStatus() != null, BizRegistration::getStatus, query.getStatus());

        // 关键字既可以是挂号号，也可以是患者姓名；姓名是跨表条件，
        // 先按姓名查出患者再收窄，比 join 更好维护
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            List<Long> patientIds = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .likeRight(BizPatient::getName, kw))
                    .stream().map(BizPatient::getId).toList();
            wrapper.and(w -> {
                w.likeRight(BizRegistration::getRegNo, kw);
                if (!patientIds.isEmpty()) {
                    w.or().in(BizRegistration::getPatientId, patientIds);
                }
            });
        }

        if (query.getStartTime() != null) {
            wrapper.ge(BizRegistration::getRegTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizRegistration::getRegTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizRegistration::getRegTime);

        Page<BizRegistration> page = registrationMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        // 患者名/医生名批量补一次，避免每行两次查询（N+1）
        NameBook names = loadNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, names));
    }

    /** 候诊队列：某科室当天的待就诊 + 就诊中，按排队号升序 */
    public List<RegistrationVO> queue(Long deptId, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<BizRegistration> records = registrationMapper.selectList(
                Wrappers.<BizRegistration>lambdaQuery()
                        .eq(BizRegistration::getDeptId, deptId)
                        .in(BizRegistration::getStatus, STATUS_WAITING, STATUS_IN_VISIT)
                        .ge(BizRegistration::getRegTime, target.atStartOfDay())
                        .lt(BizRegistration::getRegTime, target.plusDays(1).atStartOfDay())
                        .orderByAsc(BizRegistration::getQueueNo));

        NameBook names = loadNames(records);
        return records.stream().map(e -> toVO(e, names)).toList();
    }

    /** 批量装配用的一次性名字簿，避免逐行查库 */
    private record NameBook(Map<Long, String> patients, Map<Long, String> doctors) {
    }

    private NameBook loadNames(List<BizRegistration> records) {
        Set<Long> patientIds = records.stream().map(BizRegistration::getPatientId).collect(Collectors.toSet());
        Set<Long> doctorIds = records.stream().map(BizRegistration::getDoctorId).collect(Collectors.toSet());

        Map<Long, String> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patients.put(p.getId(), p.getName());
            }
        }
        Map<Long, String> doctors = new HashMap<>();
        if (!doctorIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(doctorIds)) {
                doctors.put(u.getId(), u.getRealName());
            }
        }
        return new NameBook(patients, doctors);
    }

    private RegistrationVO toVO(BizRegistration entity, NameBook names) {
        return RegistrationVO.builder()
                .id(String.valueOf(entity.getId()))
                .regNo(entity.getRegNo())
                .patientId(entity.getPatientId())
                .patientName(names.patients().get(entity.getPatientId()))
                .doctorId(entity.getDoctorId())
                .doctorName(names.doctors().get(entity.getDoctorId()))
                .deptId(entity.getDeptId())
                .scheduleId(entity.getScheduleId())
                .regType(entity.getRegType())
                .regFee(entity.getRegFee())
                .queueNo(entity.getQueueNo())
                .status(entity.getStatus())
                .regTime(entity.getRegTime())
                .build();
    }
}
