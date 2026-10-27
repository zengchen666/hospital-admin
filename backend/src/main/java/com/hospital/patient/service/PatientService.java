package com.hospital.patient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.util.HashUtil;
import com.hospital.common.util.MaskUtil;
import com.hospital.patient.dto.PatientPlainVO;
import com.hospital.patient.dto.PatientQuery;
import com.hospital.patient.dto.PatientSaveRequest;
import com.hospital.patient.dto.PatientVO;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * 患者档案服务。
 *
 * <p><b>敏感信息口径</b>：入参明文 → 落库计算 hash → 出参脱敏。
 * 全链路只有「解密查看」接口回明文，且该接口单独授权并记审计。</p>
 */
@Service
public class PatientService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final BizPatientMapper patientMapper;
    private final PatientNoGenerator noGenerator;

    public PatientService(BizPatientMapper patientMapper, PatientNoGenerator noGenerator) {
        this.patientMapper = patientMapper;
        this.noGenerator = noGenerator;
    }

    public PageResult<PatientVO> page(PatientQuery query) {
        LambdaQueryWrapper<BizPatient> wrapper = Wrappers.<BizPatient>lambdaQuery()
                .eq(query.getGender() != null, BizPatient::getGender, query.getGender())
                .eq(StringUtils.hasText(query.getBloodType()), BizPatient::getBloodType, query.getBloodType());

        String keyword = query.getKeyword();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            if (PHONE.matcher(trimmed).matches()) {
                // 手机号是敏感字段，库里没有明文可 LIKE，只能按 hash 精确命中
                wrapper.eq(BizPatient::getPhoneHash, HashUtil.sha256Hex(trimmed));
            } else {
                wrapper.and(w -> w.like(BizPatient::getName, trimmed)
                        .or().like(BizPatient::getPatientNo, trimmed));
            }
        }

        if (query.getDeptId() != null) {
            // deptId 是 Long，直接拼进子查询不存在注入面；换成字符串参数就必须走参数绑定
            wrapper.inSql(BizPatient::getId,
                    "SELECT patient_id FROM biz_registration WHERE dept_id = " + query.getDeptId()
                            + " AND deleted = 0");
        }
        if (query.getStartTime() != null) {
            wrapper.ge(BizPatient::getCreateTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizPatient::getCreateTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizPatient::getCreateTime);

        Page<BizPatient> page = patientMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        // 列表不返回病史，减少不必要的信息暴露
        return PageResult.of(page, entity -> toVO(entity, false));
    }

    public PatientVO detail(Long id) {
        return toVO(getExisting(id), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PatientSaveRequest request) {
        String idCardHash = HashUtil.sha256Hex(request.getIdCard());
        if (idCardHash != null && patientMapper.countByIdCardHash(idCardHash) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "该身份证已建档，请勿重复登记");
        }

        BizPatient entity = new BizPatient();
        applyRequest(request, entity, true, true);
        entity.setPatientNo(noGenerator.next());
        entity.setIdCardHash(idCardHash);
        entity.setPhoneHash(HashUtil.sha256Hex(request.getPhone()));
        entity.setCardBalance(BigDecimal.ZERO);

        patientMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 修改档案。
     *
     * <p><b>身份证与手机号「不传即不改」</b>：列表与详情返回的是脱敏值（4403****1234），
     * 前端改个住址时只能把这些字段原样回填或直接留空。若按「留空就清空」处理，
     * 改一次住址就会把患者的真实身份证号连 hash 一起抹掉 —— 之后查重、实名核验全部失效。
     * 这与 {@code UserSaveRequest} 里「编辑不传 password 就不改密码」是同一条原则。</p>
     *
     * <p>需要更正身份证时，走单独的更正流程（留审计），不在这里静默覆盖。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PatientSaveRequest request) {
        BizPatient entity = getExisting(id);

        boolean idCardGiven = StringUtils.hasText(request.getIdCard())
                && request.getIdCard().indexOf('*') < 0;
        boolean phoneGiven = StringUtils.hasText(request.getPhone())
                && request.getPhone().indexOf('*') < 0;

        String idCardHash = idCardGiven ? HashUtil.sha256Hex(request.getIdCard()) : null;
        if (idCardHash != null && patientMapper.countByIdCardHashExcluding(idCardHash, id) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "该身份证已被其他患者档案占用");
        }

        applyRequest(request, entity, idCardGiven, phoneGiven);
        if (idCardHash != null) {
            entity.setIdCardHash(idCardHash);
        }
        if (phoneGiven) {
            entity.setPhoneHash(HashUtil.sha256Hex(request.getPhone()));
        }
        // patient_no 不随修改变化：它是患者的长期标识，变了历史单据就对不上
        patientMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getExisting(id);
        if (patientMapper.countActiveRegistration(id) > 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                    "该患者还有未结束的挂号，请先处理后再删除");
        }
        // 逻辑删除：@TableLogic 自动改写为 UPDATE ... SET deleted = 1
        patientMapper.deleteById(id);
    }

    /** 建档前的身份证查重 */
    public boolean idCardExists(String idCard) {
        String hash = HashUtil.sha256Hex(idCard);
        return hash != null && patientMapper.countByIdCardHash(hash) > 0;
    }

    /** 解密查看明文（需 patient:decrypt 权限，调用方负责记审计） */
    public PatientPlainVO plain(Long id) {
        BizPatient entity = getExisting(id);
        return PatientPlainVO.builder()
                .id(String.valueOf(entity.getId()))
                .patientNo(entity.getPatientNo())
                .name(entity.getName())
                .idCard(entity.getIdCard())
                .phone(entity.getPhone())
                .build();
    }

    private BizPatient getExisting(Long id) {
        BizPatient entity = patientMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在或已删除");
        }
        return entity;
    }

    /**
     * 把入参套到实体上。
     *
     * @param withIdCard 是否写入身份证（false = 保留原值，见 {@link #update}）
     * @param withPhone  是否写入手机号
     */
    private void applyRequest(PatientSaveRequest request, BizPatient entity,
                              boolean withIdCard, boolean withPhone) {
        entity.setName(request.getName());
        entity.setGender(request.getGender());
        entity.setBirthDate(request.getBirthDate());
        if (withIdCard) {
            entity.setIdCard(blankToNull(request.getIdCard()));
        }
        if (withPhone) {
            entity.setPhone(blankToNull(request.getPhone()));
        }
        entity.setAddress(request.getAddress());
        entity.setBloodType(request.getBloodType());
        entity.setAllergyHistory(request.getAllergyHistory());
        entity.setMedicalHistory(request.getMedicalHistory());
        entity.setFamilyHistory(request.getFamilyHistory());
        entity.setTags(request.getTags());
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private PatientVO toVO(BizPatient entity, boolean withHistory) {
        return PatientVO.builder()
                .id(String.valueOf(entity.getId()))
                .patientNo(entity.getPatientNo())
                .name(entity.getName())
                .gender(entity.getGender())
                .age(calcAge(entity.getBirthDate()))
                .birthDate(entity.getBirthDate())
                .idCard(MaskUtil.idCard(entity.getIdCard()))
                .phone(MaskUtil.phone(entity.getPhone()))
                .address(entity.getAddress())
                .bloodType(entity.getBloodType())
                .allergyHistory(entity.getAllergyHistory())
                .medicalHistory(withHistory ? entity.getMedicalHistory() : null)
                .familyHistory(withHistory ? entity.getFamilyHistory() : null)
                .cardNo(entity.getCardNo())
                .cardBalance(entity.getCardBalance())
                .tags(entity.getTags())
                .createTime(entity.getCreateTime())
                .build();
    }

    private Integer calcAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
