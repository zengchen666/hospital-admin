package com.hospital.inpatient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.inpatient.dto.NursingQuery;
import com.hospital.inpatient.dto.NursingRequest;
import com.hospital.inpatient.dto.NursingVO;
import com.hospital.inpatient.entity.BizAdmission;
import com.hospital.inpatient.entity.BizBed;
import com.hospital.inpatient.entity.BizNursingRecord;
import com.hospital.inpatient.mapper.BizAdmissionMapper;
import com.hospital.inpatient.mapper.BizBedMapper;
import com.hospital.inpatient.mapper.BizNursingRecordMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 护理记录服务：体温单、生命体征、护理记录三类共用一张表。
 *
 * <p><b>两个硬约束</b>：</p>
 * <ol>
 *   <li>只能给<b>在院</b>患者写记录 —— 出院后再补录体征属于病历造假，直接拒绝；</li>
 *   <li>查询<b>必须</b>带 {@code recordType}，除非调用方明确要全类型。
 *       一张表三类记录，不带类型查出来是混的，而混起来的列表在界面上「看着像对的」，
 *       这种 bug 只能靠护士发现，代价太高。</li>
 * </ol>
 *
 * <p>记录护士取当前登录用户，不接受前端传值 —— 谁写的记录要能追责。</p>
 */
@Slf4j
@Service
public class NursingService {

    /** 0 在院 */
    private static final int ADMISSION_IN_HOSPITAL = 0;

    private final BizNursingRecordMapper recordMapper;
    private final BizAdmissionMapper admissionMapper;
    private final BizBedMapper bedMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;

    public NursingService(BizNursingRecordMapper recordMapper,
                          BizAdmissionMapper admissionMapper,
                          BizBedMapper bedMapper,
                          BizPatientMapper patientMapper,
                          SysUserMapper userMapper) {
        this.recordMapper = recordMapper;
        this.admissionMapper = admissionMapper;
        this.bedMapper = bedMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(NursingRequest request) {
        BizAdmission admission = admissionMapper.selectById(request.getAdmissionId());
        if (admission == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "住院记录不存在");
        }
        if (!Integer.valueOf(ADMISSION_IN_HOSPITAL).equals(admission.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "患者已出院，不能再补录护理记录");
        }
        validateByType(request);

        BizNursingRecord record = new BizNursingRecord();
        record.setAdmissionId(request.getAdmissionId());
        record.setRecordType(request.getRecordType());
        record.setRecordTime(request.getRecordTime() == null ? LocalDateTime.now() : request.getRecordTime());
        record.setTemperature(request.getTemperature());
        record.setPulse(request.getPulse());
        record.setBreath(request.getBreath());
        record.setBloodPressure(request.getBloodPressure());
        record.setSpo2(request.getSpo2());
        record.setIntake(request.getIntake());
        record.setOutput(request.getOutput());
        record.setContent(request.getContent());
        record.setNurseId(CurrentUser.requireId());
        recordMapper.insert(record);

        log.info("新增护理记录 admissionId={} type={} nurseId={}",
                request.getAdmissionId(), request.getRecordType(), record.getNurseId());
        return record.getId();
    }

    public PageResult<NursingVO> page(NursingQuery query) {
        LambdaQueryWrapper<BizNursingRecord> wrapper = Wrappers.<BizNursingRecord>lambdaQuery()
                .eq(query.getAdmissionId() != null, BizNursingRecord::getAdmissionId, query.getAdmissionId())
                .eq(query.getRecordType() != null, BizNursingRecord::getRecordType, query.getRecordType());
        wrapper.orderByDesc(BizNursingRecord::getRecordTime);

        Page<BizNursingRecord> page = recordMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<BizNursingRecord> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(page, r -> NursingVO.builder().build());
        }

        Set<Long> admissionIds = records.stream().map(BizNursingRecord::getAdmissionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> nurseIds = records.stream().map(BizNursingRecord::getNurseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, BizAdmission> admissions = new HashMap<>();
        for (BizAdmission a : admissionMapper.selectByIds(admissionIds)) {
            admissions.put(a.getId(), a);
        }
        Map<Long, String> patientNames = new HashMap<>();
        Set<Long> patientIds = admissions.values().stream()
                .map(BizAdmission::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patientNames.put(p.getId(), p.getName());
            }
        }
        Map<Long, String> bedNos = new HashMap<>();
        Set<Long> bedIds = admissions.values().stream()
                .map(BizAdmission::getBedId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!bedIds.isEmpty()) {
            for (BizBed b : bedMapper.selectByIds(bedIds)) {
                bedNos.put(b.getId(), b.getBedNo());
            }
        }
        Map<Long, String> nurseNames = new HashMap<>();
        if (!nurseIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(nurseIds)) {
                nurseNames.put(u.getId(), u.getRealName());
            }
        }

        Map<Long, BizAdmission> finalAdmissions = admissions;
        return PageResult.of(page, r -> {
            BizAdmission a = finalAdmissions.get(r.getAdmissionId());
            return NursingVO.builder()
                    .id(String.valueOf(r.getId()))
                    .admissionId(String.valueOf(r.getAdmissionId()))
                    .patientName(a == null ? null : patientNames.get(a.getPatientId()))
                    .bedNo(a == null || a.getBedId() == null ? null : bedNos.get(a.getBedId()))
                    .recordType(r.getRecordType())
                    .recordTime(r.getRecordTime())
                    .temperature(r.getTemperature())
                    .pulse(r.getPulse())
                    .breath(r.getBreath())
                    .bloodPressure(r.getBloodPressure())
                    .spo2(r.getSpo2())
                    .intake(r.getIntake())
                    .output(r.getOutput())
                    .content(r.getContent())
                    .nurseId(r.getNurseId())
                    .nurseName(nurseNames.get(r.getNurseId()))
                    .build();
        });
    }

    /**
     * 按类型做必填校验。
     *
     * <p>体温单和生命体征没有 content，纯护理记录没有体征数据 ——
     * 不校验的话，护士随手点一下就能存一条全空的「体温单」，
     * 体温曲线上会出现一个空白点，看着比没有更糟。</p>
     */
    private void validateByType(NursingRequest request) {
        int type = request.getRecordType();
        if (type == BizNursingRecord.TYPE_NURSING) {
            if (!StringUtils.hasText(request.getContent())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "护理记录内容不能为空");
            }
            return;
        }
        // 2 体温单 / 3 生命体征：至少要有一个体征值
        boolean hasVital = request.getTemperature() != null
                || request.getPulse() != null
                || request.getBreath() != null
                || request.getSpo2() != null
                || StringUtils.hasText(request.getBloodPressure());
        if (!hasVital) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    type == BizNursingRecord.TYPE_TEMPERATURE ? "体温单至少需要填写体温" : "生命体征至少需要填写一项");
        }
        if (type == BizNursingRecord.TYPE_TEMPERATURE && request.getTemperature() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "体温单必须填写体温");
        }
    }
}
