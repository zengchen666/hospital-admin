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
import com.hospital.publichealth.dto.CheckupQuery;
import com.hospital.publichealth.dto.CheckupReportRequest;
import com.hospital.publichealth.dto.CheckupRequest;
import com.hospital.publichealth.dto.CheckupVO;
import com.hospital.publichealth.entity.BizCheckup;
import com.hospital.publichealth.entity.BizCheckupPackage;
import com.hospital.publichealth.mapper.BizCheckupMapper;
import com.hospital.publichealth.mapper.BizCheckupPackageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 体检记录服务。
 *
 * <p>状态流转：<b>0 待检 → 1 已检待出报告 → 2 已出报告</b>，全程条件更新。</p>
 *
 * <p>两条必须守住的规矩：</p>
 * <ol>
 *   <li><b>套餐名与价格存快照</b>：套餐会改名改价，体检单只存 package_id 的话，
 *       一年前买的套餐按今天的价去算，账就对不上了（与处方明细存单价快照同理）；</li>
 *   <li><b>出报告必须从「已检」走</b>：{@code publishReport} 的 WHERE 带
 *       {@code status = 1}，待检的单子跳过检查直接出报告，等于伪造体检结果。</li>
 * </ol>
 */
@Slf4j
@Service
public class CheckupService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CHECKED = 1;
    private static final int STATUS_REPORTED = 2;

    private final BizCheckupMapper checkupMapper;
    private final BizCheckupPackageMapper packageMapper;
    private final BizPatientMapper patientMapper;
    private final CheckupNoGenerator noGenerator;

    public CheckupService(BizCheckupMapper checkupMapper,
                          BizCheckupPackageMapper packageMapper,
                          BizPatientMapper patientMapper,
                          CheckupNoGenerator noGenerator) {
        this.checkupMapper = checkupMapper;
        this.packageMapper = packageMapper;
        this.patientMapper = patientMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 体检登记。
     *
     * <p>金额只取套餐价：散查（无套餐）时记 0，后续按单项检查单独收费。
     * <b>不接受前端传价</b>，理由与处方/检查收费一致。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(CheckupRequest request) {
        BizPatient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在，请先建档");
        }
        if (request.getCheckupDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "体检日期不能晚于今天");
        }

        String packageName = null;
        BigDecimal amount = BigDecimal.ZERO;
        if (request.getPackageId() != null) {
            BizCheckupPackage pkg = packageMapper.selectById(request.getPackageId());
            if (pkg == null) {
                throw new BusinessException(ErrorCode.NO_RESOURCE, "体检套餐不存在");
            }
            if (!Integer.valueOf(1).equals(pkg.getStatus())) {
                throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该体检套餐已停用");
            }
            packageName = pkg.getPackageName();
            amount = pkg.getPrice();
        }

        BizCheckup entity = new BizCheckup();
        entity.setCheckupNo(noGenerator.next());
        entity.setPatientId(patient.getId());
        entity.setPackageId(request.getPackageId());
        entity.setPackageName(packageName);
        entity.setAmount(amount);
        entity.setCheckupDate(request.getCheckupDate());
        entity.setStatus(STATUS_PENDING);
        entity.setCreateBy(CurrentUser.idOrNull());
        checkupMapper.insert(entity);

        log.info("体检登记成功 checkupNo={} patientId={} package={} amount={}",
                entity.getCheckupNo(), patient.getId(), packageName, amount);
        return entity.getId();
    }

    /** 标记已检：0 待检 → 1 已检待出报告 */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        BizCheckup entity = requireCheckup(id);
        if (Integer.valueOf(STATUS_CHECKED).equals(entity.getStatus())
                || Integer.valueOf(STATUS_REPORTED).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该体检单已完成检查");
        }
        transit(id, STATUS_PENDING, STATUS_CHECKED);
    }

    /**
     * 出具体检报告：1 已检待出报告 → 2 已出报告。
     *
     * <p>报告一旦出具<b>不可再改</b>：受检者可能已经拿着报告去复诊或入职体检了，
     * 悄悄改结论会让他手里的纸质件与系统对不上。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishReport(Long id, CheckupReportRequest request) {
        BizCheckup entity = requireCheckup(id);
        if (Integer.valueOf(STATUS_REPORTED).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该体检报告已出具，不能重复出具");
        }
        if (!Integer.valueOf(STATUS_CHECKED).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "请先完成检查再出具报告");
        }
        Long operatorId = CurrentUser.idOrNull();
        if (checkupMapper.publishReport(id, request.getSummary().trim(),
                normalizeAbnormal(request.getAbnormalItems()), operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "体检状态已变更，请刷新后重试");
        }
        if (request.getReportFileId() != null) {
            BizCheckup attach = new BizCheckup();
            attach.setId(id);
            attach.setReportFileId(request.getReportFileId());
            attach.setUpdateBy(operatorId);
            checkupMapper.updateById(attach);
        }
        log.info("体检报告出具成功 checkupNo={}", entity.getCheckupNo());
    }

    public PageResult<CheckupVO> page(CheckupQuery query) {
        LambdaQueryWrapper<BizCheckup> wrapper = Wrappers.<BizCheckup>lambdaQuery()
                .eq(query.getPatientId() != null, BizCheckup::getPatientId, query.getPatientId())
                .eq(query.getPackageId() != null, BizCheckup::getPackageId, query.getPackageId())
                .eq(query.getStatus() != null, BizCheckup::getStatus, query.getStatus());

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            List<Long> matched = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .like(BizPatient::getName, kw))
                    .stream().map(BizPatient::getId).toList();
            wrapper.and(w -> w.like(BizCheckup::getCheckupNo, kw)
                    .or(!matched.isEmpty(), q -> q.in(BizCheckup::getPatientId, matched)));
        }
        if (query.getStartTime() != null) {
            wrapper.ge(BizCheckup::getCheckupDate, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(BizCheckup::getCheckupDate, query.getEndTime());
        }
        wrapper.orderByDesc(BizCheckup::getCheckupDate).orderByDesc(BizCheckup::getId);

        Page<BizCheckup> page = checkupMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, BizPatient> patients = loadPatients(
                page.getRecords().stream().map(BizCheckup::getPatientId).collect(Collectors.toSet()));
        return PageResult.of(page, entity -> toVO(entity, patients));
    }

    public CheckupVO detail(Long id) {
        BizCheckup entity = requireCheckup(id);
        return toVO(entity, loadPatients(Set.of(entity.getPatientId())));
    }

    private void transit(Long id, Integer from, Integer to) {
        if (checkupMapper.transitStatus(id, from, to, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "体检状态已变更，请刷新后重试");
        }
    }

    private BizCheckup requireCheckup(Long id) {
        BizCheckup entity = checkupMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "体检记录不存在");
        }
        return entity;
    }

    /** 异常项去空格并统一分隔符，避免列表里出现空项 */
    private String normalizeAbnormal(String abnormalItems) {
        if (!StringUtils.hasText(abnormalItems)) {
            return null;
        }
        String joined = Arrays.stream(abnormalItems.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private Map<Long, BizPatient> loadPatients(Set<Long> ids) {
        Set<Long> valid = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, BizPatient> map = new HashMap<>();
        for (BizPatient p : patientMapper.selectByIds(valid)) {
            map.put(p.getId(), p);
        }
        return map;
    }

    private CheckupVO toVO(BizCheckup entity, Map<Long, BizPatient> patients) {
        BizPatient patient = patients.get(entity.getPatientId());
        return CheckupVO.builder()
                .id(String.valueOf(entity.getId()))
                .checkupNo(entity.getCheckupNo())
                .patientId(entity.getPatientId())
                .patientName(patient == null ? null : patient.getName())
                .gender(patient == null ? null : patient.getGender())
                .age(patient == null ? null : calcAge(patient.getBirthDate()))
                .packageId(entity.getPackageId())
                .packageName(entity.getPackageName())
                .amount(entity.getAmount() == null ? BigDecimal.ZERO : entity.getAmount())
                .checkupDate(entity.getCheckupDate())
                .summary(entity.getSummary())
                .abnormalItems(entity.getAbnormalItems())
                .reportFileId(entity.getReportFileId())
                .status(entity.getStatus())
                .build();
    }

    private Integer calcAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
