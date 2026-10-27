package com.hospital.clinic.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.clinic.dto.ReceiveResult;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

/**
 * 接诊服务：把「待就诊」推进到「就诊中」，并返回医生工作台首屏数据。
 *
 * <p>接诊必须校验<b>数据权限</b>：医生只能接诊挂在自己名下的号。
 * 否则任何登录用户改一下 URL 里的 id 就能把别人的患者接走。</p>
 */
@Slf4j
@Service
public class ClinicVisitService {

    private static final int STATUS_WAITING = 0;
    private static final int STATUS_IN_VISIT = 1;
    private static final int STATUS_FINISHED = 2;
    private static final int STATUS_CANCELLED = 3;
    private static final String ADMIN_ROLE = "admin";

    private final BizRegistrationMapper registrationMapper;
    private final BizPatientMapper patientMapper;
    private final BizMedicalRecordMapper recordMapper;

    public ClinicVisitService(BizRegistrationMapper registrationMapper,
                              BizPatientMapper patientMapper,
                              BizMedicalRecordMapper recordMapper) {
        this.registrationMapper = registrationMapper;
        this.patientMapper = patientMapper;
        this.recordMapper = recordMapper;
    }

    /**
     * 接诊：挂号 0 → 1（幂等，已是就诊中则直接返回工作台数据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReceiveResult receive(Long visitId) {
        BizRegistration registration = registrationMapper.selectById(visitId);
        if (registration == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "挂号记录不存在");
        }
        if (Integer.valueOf(STATUS_CANCELLED).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该挂号已退号，无法接诊");
        }
        if (Integer.valueOf(STATUS_FINISHED).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该次就诊已完成");
        }
        assertOwnedByCurrentDoctor(registration);

        if (Integer.valueOf(STATUS_WAITING).equals(registration.getStatus())) {
            int affected = registrationMapper.update(null, Wrappers.<BizRegistration>lambdaUpdate()
                    .eq(BizRegistration::getId, visitId)
                    .eq(BizRegistration::getStatus, STATUS_WAITING)
                    .set(BizRegistration::getStatus, STATUS_IN_VISIT)
                    .set(BizRegistration::getUpdateTime, java.time.LocalDateTime.now()));
            if (affected == 0) {
                // 并发：另一个请求刚接诊了，重新读一次判断状态
                BizRegistration latest = registrationMapper.selectById(visitId);
                if (latest == null || !Integer.valueOf(STATUS_IN_VISIT).equals(latest.getStatus())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "挂号状态已变化，请刷新后重试");
                }
                registration = latest;
            } else {
                registration.setStatus(STATUS_IN_VISIT);
            }
            log.info("接诊成功 visitId={} regNo={} doctorId={}",
                    visitId, registration.getRegNo(), registration.getDoctorId());
        }

        BizPatient patient = patientMapper.selectById(registration.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者档案不存在");
        }
        BizMedicalRecord record = recordMapper.selectByVisitId(visitId);

        return ReceiveResult.builder()
                .visitId(String.valueOf(registration.getId()))
                .regNo(registration.getRegNo())
                .queueNo(registration.getQueueNo())
                .patientId(patient.getId())
                .patientNo(patient.getPatientNo())
                .patientName(patient.getName())
                .gender(patient.getGender())
                .age(calcAge(patient))
                .allergyHistory(patient.getAllergyHistory())
                .visitStatus(registration.getStatus())
                .recordId(record == null ? null : String.valueOf(record.getId()))
                .recordStatus(record == null ? null : record.getStatus())
                .build();
    }

    /**
     * 数据权限：非管理员只能操作自己的挂号。
     *
     * <p>用 40302（无权访问该数据）而不是 40301，语义上区分「没有这个功能权限」
     * 与「有权限但不能碰这条数据」。</p>
     */
    private void assertOwnedByCurrentDoctor(BizRegistration registration) {
        LoginUser user = CurrentUser.orNull();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getRoleCodes().contains(ADMIN_ROLE)) {
            return;
        }
        if (!user.getUserId().equals(registration.getDoctorId())) {
            throw new BusinessException(ErrorCode.DATA_FORBIDDEN, "只能接诊挂在自己名下的号");
        }
    }

    private Integer calcAge(BizPatient patient) {
        if (patient.getBirthDate() == null) {
            return null;
        }
        return Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
    }
}
