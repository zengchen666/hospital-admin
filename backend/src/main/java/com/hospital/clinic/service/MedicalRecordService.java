package com.hospital.clinic.service;

import com.hospital.clinic.dto.MedicalRecordRequest;
import com.hospital.clinic.dto.MedicalRecordVO;
import com.hospital.clinic.entity.BizMedicalRecord;
import com.hospital.clinic.mapper.BizMedicalRecordMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 门诊病历服务。
 *
 * <p><b>一次挂号一份病历</b>（{@code uk_record_visit}）。保存接口同时承担
 * 「存草稿」与「提交」两种语义：草稿可反复覆盖，提交后锁定不可改 ——
 * 病历是要承担法律责任的文书，不能像普通表单那样随便编辑。</p>
 */
@Service
public class MedicalRecordService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_CANCELLED = 3;
    private static final String ADMIN_ROLE = "admin";

    private final BizMedicalRecordMapper recordMapper;
    private final BizRegistrationMapper registrationMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;

    public MedicalRecordService(BizMedicalRecordMapper recordMapper,
                                BizRegistrationMapper registrationMapper,
                                BizPatientMapper patientMapper,
                                SysUserMapper userMapper) {
        this.recordMapper = recordMapper;
        this.registrationMapper = registrationMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
    }

    /**
     * 保存病历：{@code submit=false} 存草稿，{@code true} 提交并锁定。
     *
     * @return 病历 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(MedicalRecordRequest request) {
        BizRegistration registration = registrationMapper.selectById(request.getVisitId());
        if (registration == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "挂号记录不存在");
        }
        if (Integer.valueOf(STATUS_CANCELLED).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已退号的挂号不能书写病历");
        }
        assertOwnedByCurrentDoctor(registration);

        // 先判病历状态，再做内容校验：病历已提交时，
        // 无论请求内容是否合法都应该回「已提交不能改」，
        // 否则用户看到「诊断不能为空」会以为补上诊断就能改，白折腾一轮
        BizMedicalRecord existing = recordMapper.selectByVisitId(request.getVisitId());
        if (existing != null && !Integer.valueOf(STATUS_DRAFT).equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                    "病历已提交，不允许直接修改；如需更正请联系管理员作废重开");
        }

        boolean submit = Boolean.TRUE.equals(request.getSubmit());
        if (submit) {
            if (!StringUtils.hasText(request.getChiefComplaint())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "提交病历时主诉不能为空");
            }
            if (!StringUtils.hasText(request.getDiagnosis())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "提交病历时诊断不能为空");
            }
        }

        int targetStatus = submit ? STATUS_SUBMITTED : STATUS_DRAFT;
        if (existing == null) {
            BizMedicalRecord entity = new BizMedicalRecord();
            entity.setPatientId(registration.getPatientId());
            entity.setVisitId(registration.getId());
            // 就诊流水号直接复用挂号单号，两个号能对得上，人工核对不用查表
            entity.setVisitNo(registration.getRegNo());
            entity.setVisitType(1);
            entity.setDoctorId(registration.getDoctorId());
            entity.setVisitTime(LocalDateTime.now());
            entity.setStatus(targetStatus);
            applyContent(request, entity);
            recordMapper.insert(entity);
            return entity.getId();
        }

        existing.setStatus(targetStatus);
        applyContent(request, existing);
        recordMapper.updateById(existing);
        return existing.getId();
    }

    /** 按就诊 ID 查病历，不存在返回 null（接诊首屏要能容忍"还没写"） */
    public MedicalRecordVO getByVisit(Long visitId) {
        BizMedicalRecord entity = recordMapper.selectByVisitId(visitId);
        if (entity == null) {
            return null;
        }
        BizPatient patient = patientMapper.selectById(entity.getPatientId());
        SysUser doctor = entity.getDoctorId() == null ? null : userMapper.selectById(entity.getDoctorId());

        return MedicalRecordVO.builder()
                .id(String.valueOf(entity.getId()))
                .visitId(String.valueOf(entity.getVisitId()))
                .visitNo(entity.getVisitNo())
                .patientId(entity.getPatientId())
                .patientName(patient == null ? null : patient.getName())
                .doctorId(entity.getDoctorId())
                .doctorName(doctor == null ? null : doctor.getRealName())
                .visitType(entity.getVisitType())
                .chiefComplaint(entity.getChiefComplaint())
                .presentIllness(entity.getPresentIllness())
                .physicalExam(entity.getPhysicalExam())
                .diagnosis(entity.getDiagnosis())
                .icdCode(entity.getIcdCode())
                .visitTime(entity.getVisitTime())
                .status(entity.getStatus())
                .build();
    }

    private void applyContent(MedicalRecordRequest request, BizMedicalRecord entity) {
        entity.setChiefComplaint(request.getChiefComplaint());
        entity.setPresentIllness(request.getPresentIllness());
        entity.setPhysicalExam(request.getPhysicalExam());
        entity.setDiagnosis(request.getDiagnosis());
        entity.setIcdCode(request.getIcdCode());
    }

    private void assertOwnedByCurrentDoctor(BizRegistration registration) {
        LoginUser user = CurrentUser.orNull();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getRoleCodes().contains(ADMIN_ROLE)) {
            return;
        }
        if (!user.getUserId().equals(registration.getDoctorId())) {
            throw new BusinessException(ErrorCode.DATA_FORBIDDEN, "只能书写自己接诊患者的病历");
        }
    }
}
