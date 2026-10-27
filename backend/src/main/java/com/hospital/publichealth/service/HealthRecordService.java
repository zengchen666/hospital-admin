package com.hospital.publichealth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.publichealth.dto.HealthRecordQuery;
import com.hospital.publichealth.dto.HealthRecordRequest;
import com.hospital.publichealth.dto.HealthRecordVO;
import com.hospital.publichealth.entity.BizHealthRecord;
import com.hospital.publichealth.mapper.BizHealthRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 居民健康档案服务。
 *
 * <p><b>一人一份</b>：建档前先查 {@code countByPatient}，
 * 重复建档直接 40904。居民的慢病随访、体检结论都挂在档案上，
 * 出现两份会导致同一个人的随访记录分散在两处。</p>
 *
 * <p><b>更新不换号</b>：档案号一旦生成终身不变，
 * 即使档案被删除后重建也复用同一个 patient_id 维度（唯一键管着）。</p>
 */
@Slf4j
@Service
public class HealthRecordService {

    private final BizHealthRecordMapper recordMapper;
    private final BizPatientMapper patientMapper;
    private final HealthRecordNoGenerator noGenerator;

    public HealthRecordService(BizHealthRecordMapper recordMapper,
                               BizPatientMapper patientMapper,
                               HealthRecordNoGenerator noGenerator) {
        this.recordMapper = recordMapper;
        this.patientMapper = patientMapper;
        this.noGenerator = noGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(HealthRecordRequest request) {
        BizPatient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在，请先建档");
        }
        if (recordMapper.countByPatient(request.getPatientId()) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA,
                    "该居民已建立健康档案，不能重复建档");
        }

        BizHealthRecord record = new BizHealthRecord();
        record.setRecordNo(noGenerator.next());
        record.setPatientId(request.getPatientId());
        record.setChronicDisease(request.getChronicDisease());
        record.setSmoking(request.getSmoking() == null ? 0 : request.getSmoking());
        record.setDrinking(request.getDrinking() == null ? 0 : request.getDrinking());
        record.setExercise(request.getExercise());
        record.setCreateBy(CurrentUser.idOrNull());
        recordMapper.insert(record);

        log.info("健康档案建档成功 recordNo={} patientId={}", record.getRecordNo(), patient.getId());
        return record.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, HealthRecordRequest request) {
        BizHealthRecord record = requireRecord(id);
        record.setChronicDisease(request.getChronicDisease());
        record.setSmoking(request.getSmoking() == null ? record.getSmoking() : request.getSmoking());
        record.setDrinking(request.getDrinking() == null ? record.getDrinking() : request.getDrinking());
        record.setExercise(request.getExercise());
        record.setUpdateBy(CurrentUser.idOrNull());
        recordMapper.updateById(record);
    }

    public PageResult<HealthRecordVO> page(HealthRecordQuery query) {
        LambdaQueryWrapper<BizHealthRecord> wrapper = Wrappers.<BizHealthRecord>lambdaQuery()
                .eq(query.getPatientId() != null, BizHealthRecord::getPatientId, query.getPatientId())
                .eq(query.getSmoking() != null, BizHealthRecord::getSmoking, query.getSmoking());

        if (StringUtils.hasText(query.getChronicDisease())) {
            wrapper.like(BizHealthRecord::getChronicDisease, query.getChronicDisease());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            // 关键字同时命中档案号与居民姓名：先按姓名找到患者，再按档案号直接匹配
            List<Long> matched = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .like(BizPatient::getName, kw))
                    .stream().map(BizPatient::getId).toList();
            wrapper.and(w -> w.like(BizHealthRecord::getRecordNo, kw)
                    .or(!matched.isEmpty(), q -> q.in(BizHealthRecord::getPatientId, matched)));
        }
        wrapper.orderByDesc(BizHealthRecord::getCreateTime);

        Page<BizHealthRecord> page = recordMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, BizPatient> patients = loadPatients(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, patients));
    }

    public HealthRecordVO detail(Long id) {
        BizHealthRecord record = requireRecord(id);
        Map<Long, BizPatient> patients = loadPatients(List.of(record));
        return toVO(record, patients);
    }

    private BizHealthRecord requireRecord(Long id) {
        BizHealthRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "健康档案不存在");
        }
        return record;
    }

    private Map<Long, BizPatient> loadPatients(List<BizHealthRecord> records) {
        Set<Long> ids = records.stream().map(BizHealthRecord::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, BizPatient> map = new HashMap<>();
        for (BizPatient p : patientMapper.selectByIds(ids)) {
            map.put(p.getId(), p);
        }
        return map;
    }

    private HealthRecordVO toVO(BizHealthRecord entity, Map<Long, BizPatient> patients) {
        BizPatient patient = patients.get(entity.getPatientId());
        return HealthRecordVO.builder()
                .id(String.valueOf(entity.getId()))
                .patientId(entity.getPatientId())
                .patientName(patient == null ? null : patient.getName())
                .gender(patient == null ? null : patient.getGender())
                .age(patient == null ? null : calcAge(patient.getBirthDate()))
                .recordNo(entity.getRecordNo())
                .chronicDisease(entity.getChronicDisease())
                .smoking(entity.getSmoking())
                .drinking(entity.getDrinking())
                .exercise(entity.getExercise())
                .createTime(entity.getCreateTime())
                .build();
    }

    /** 年龄实时算，不读缓存字段 —— 缓存的年龄会过期，公卫年报按年龄分档会算错 */
    private Integer calcAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
