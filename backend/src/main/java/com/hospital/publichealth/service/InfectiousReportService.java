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
import com.hospital.publichealth.dto.InfectiousQuery;
import com.hospital.publichealth.dto.InfectiousRequest;
import com.hospital.publichealth.dto.InfectiousVO;
import com.hospital.publichealth.entity.BizInfectiousReport;
import com.hospital.publichealth.mapper.BizInfectiousReportMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 传染病上报服务。
 *
 * <p><b>这不是普通业务单据，是法定文书</b>：《传染病防治法》要求甲类及按甲类管理的
 * 传染病 2 小时内上报、乙类 24 小时内上报。因此这里定下三条硬规则：</p>
 * <ol>
 *   <li>报告日期<b>不得晚于今天</b> —— 提前登记等于伪造上报时间；</li>
 *   <li>同一患者同一病种<b>不允许有两条待上报卡片</b> —— 重复上报会污染疾控直报系统；</li>
 *   <li>上报后<b>不允许修改或删除</b>，疾控反馈只能追加到 {@code cdcResponse}。</li>
 * </ol>
 */
@Slf4j
@Service
public class InfectiousReportService {

    /** 一期无病种字典，统一按乙类 24 小时核算超期；接入字典后按病种取值 */
    private static final long LEGAL_DEADLINE_HOURS = 24L;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_REPORTED = 1;

    private final BizInfectiousReportMapper reportMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;

    public InfectiousReportService(BizInfectiousReportMapper reportMapper,
                                   BizPatientMapper patientMapper,
                                   SysUserMapper userMapper) {
        this.reportMapper = reportMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
    }

    /** 填卡：落一条「待上报」的卡片，此时还没报出去 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InfectiousRequest request) {
        BizPatient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在，请先建档");
        }
        if (request.getReportDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "报告日期不能晚于今天");
        }
        if (reportMapper.countPending(patient.getId(), request.getDiseaseName()) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA,
                    "该患者「" + request.getDiseaseName() + "」已有待上报卡片，请勿重复填卡");
        }

        BizInfectiousReport entity = new BizInfectiousReport();
        entity.setPatientId(patient.getId());
        entity.setDiseaseName(request.getDiseaseName());
        entity.setIcdCode(request.getIcdCode());
        entity.setReportDate(request.getReportDate());
        entity.setReporterId(CurrentUser.idOrNull());
        entity.setReportStatus(STATUS_PENDING);
        entity.setCreateBy(CurrentUser.idOrNull());
        reportMapper.insert(entity);

        log.info("传染病报告卡登记 id={} patientId={} disease={}",
                entity.getId(), patient.getId(), entity.getDiseaseName());
        return entity.getId();
    }

    /**
     * 上报疾控：0 待上报 → 1 已上报。
     *
     * <p>条件更新 {@code AND report_status = 0} 是关键：两个人同时点上报，
     * 只有一个能成功，另一个拿到 40900，不会向疾控发两遍。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void report(Long id) {
        BizInfectiousReport entity = requireReport(id);
        if (Integer.valueOf(STATUS_REPORTED).equals(entity.getReportStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该卡片已上报，不能重复上报");
        }
        Long reporterId = CurrentUser.requireId();
        if (reportMapper.report(id, reporterId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "卡片状态已变更，请刷新后重试");
        }
        log.info("传染病上报成功 id={} disease={} reporterId={}", id, entity.getDiseaseName(), reporterId);
    }

    /** 登记疾控反馈：只追加，不改病名与日期 */
    @Transactional(rollbackFor = Exception.class)
    public void feedback(Long id, String response) {
        BizInfectiousReport entity = requireReport(id);
        if (!StringUtils.hasText(response)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "疾控反馈不能为空");
        }
        if (reportMapper.feedback(id, response.trim(), CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "卡片状态已变更，请刷新后重试");
        }
    }

    public PageResult<InfectiousVO> page(InfectiousQuery query) {
        LambdaQueryWrapper<BizInfectiousReport> wrapper = Wrappers.<BizInfectiousReport>lambdaQuery()
                .eq(query.getPatientId() != null, BizInfectiousReport::getPatientId, query.getPatientId())
                .eq(query.getReportStatus() != null,
                        BizInfectiousReport::getReportStatus, query.getReportStatus());

        if (StringUtils.hasText(query.getDiseaseName())) {
            wrapper.like(BizInfectiousReport::getDiseaseName, query.getDiseaseName());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            List<Long> matched = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .like(BizPatient::getName, kw))
                    .stream().map(BizPatient::getId).toList();
            wrapper.and(w -> w.like(BizInfectiousReport::getDiseaseName, kw)
                    .or(!matched.isEmpty(), q -> q.in(BizInfectiousReport::getPatientId, matched)));
        }
        if (query.getStartTime() != null) {
            wrapper.ge(BizInfectiousReport::getReportDate, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(BizInfectiousReport::getReportDate, query.getEndTime());
        }
        wrapper.orderByDesc(BizInfectiousReport::getReportDate).orderByDesc(BizInfectiousReport::getId);

        Page<BizInfectiousReport> page = reportMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        Map<Long, String> names = loadPatientNames(
                page.getRecords().stream().map(BizInfectiousReport::getPatientId).collect(Collectors.toSet()));
        Map<Long, String> userNames = loadUserNames(page.getRecords().stream()
                .map(BizInfectiousReport::getReporterId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResult.of(page, entity -> toVO(entity, names, userNames));
    }

    public InfectiousVO detail(Long id) {
        BizInfectiousReport entity = requireReport(id);
        Map<Long, String> names = loadPatientNames(Set.of(entity.getPatientId()));
        Map<Long, String> userNames = entity.getReporterId() == null
                ? Collections.emptyMap()
                : loadUserNames(Set.of(entity.getReporterId()));
        return toVO(entity, names, userNames);
    }

    private BizInfectiousReport requireReport(Long id) {
        BizInfectiousReport entity = reportMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "传染病报告卡不存在");
        }
        return entity;
    }

    private Map<Long, String> loadPatientNames(Set<Long> ids) {
        Set<Long> valid = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (BizPatient p : patientMapper.selectByIds(valid)) {
            map.put(p.getId(), p.getName());
        }
        return map;
    }

    private Map<Long, String> loadUserNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysUser u : userMapper.selectByIds(ids)) {
            map.put(u.getId(), u.getRealName());
        }
        return map;
    }

    private InfectiousVO toVO(BizInfectiousReport entity,
                              Map<Long, String> names,
                              Map<Long, String> userNames) {
        return InfectiousVO.builder()
                .id(String.valueOf(entity.getId()))
                .patientId(entity.getPatientId())
                .patientName(names.get(entity.getPatientId()))
                .diseaseName(entity.getDiseaseName())
                .icdCode(entity.getIcdCode())
                .reportDate(entity.getReportDate())
                .reporterId(entity.getReporterId())
                .reporterName(userNames.get(entity.getReporterId()))
                .reportStatus(entity.getReportStatus())
                .cdcResponse(entity.getCdcResponse())
                .createTime(entity.getCreateTime())
                .overdueHours(calcOverdueHours(entity))
                .build();
    }

    /**
     * 超期小时数：已上报为 0；待上报则按「填卡时间 + 24 小时」的法定时限核算。
     *
     * <p>真实口径应以<b>诊断时间</b>起算，一期表里没有该字段，
     * 用填卡时间近似 —— 这个值只用于院内提醒，不作为对外报送依据。</p>
     */
    private Long calcOverdueHours(BizInfectiousReport entity) {
        if (Integer.valueOf(STATUS_REPORTED).equals(entity.getReportStatus())
                || entity.getCreateTime() == null) {
            return 0L;
        }
        LocalDateTime deadline = entity.getCreateTime().plusHours(LEGAL_DEADLINE_HOURS);
        long hours = Duration.between(deadline, LocalDateTime.now()).toHours();
        return Math.max(0L, hours);
    }
}
