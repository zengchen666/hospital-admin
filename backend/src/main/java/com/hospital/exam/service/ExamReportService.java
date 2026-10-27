package com.hospital.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import com.hospital.exam.dto.ExamApplyQuery;
import com.hospital.exam.dto.ExamItemVO;
import com.hospital.exam.dto.ExamReportQuery;
import com.hospital.exam.dto.ExamReportVO;
import com.hospital.exam.dto.ExamResultItemRequest;
import com.hospital.exam.dto.ExamResultRequest;
import com.hospital.exam.entity.BizExamApply;
import com.hospital.exam.entity.BizExamItem;
import com.hospital.exam.entity.BizExamReport;
import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamItemMapper;
import com.hospital.exam.mapper.BizExamReportMapper;
import com.hospital.message.service.MessageService;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检查检验报告服务：录入、审核发布、危急值查询。
 *
 * <p><b>三条规则</b>：</p>
 * <ol>
 *   <li>已发布的报告不可再改 —— 报告一旦被临床看到就不能悄悄改数；</li>
 *   <li>不能审核自己录入的报告 —— 与处方审核同样的制衡；</li>
 *   <li>危急值必须独立标记并在发布后进入待处理列表 —— 漏通知是医疗事故。</li>
 * </ol>
 */
@Slf4j
@Service
public class ExamReportService {

    private static final int APPLY_WAITING_SAMPLE = 1;
    private static final int APPLY_TESTING = 2;
    private static final int APPLY_REPORTED = 3;

    private static final int REPORT_DRAFT = 0;
    private static final int REPORT_PUBLISHED = 1;

    private static final String ADMIN_ROLE = "admin";

    private final BizExamReportMapper reportMapper;
    private final BizExamItemMapper itemMapper;
    private final BizExamApplyMapper applyMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final ExamNoGenerator noGenerator;
    private final MessageService messageService;

    public ExamReportService(BizExamReportMapper reportMapper,
                             BizExamItemMapper itemMapper,
                             BizExamApplyMapper applyMapper,
                             BizPatientMapper patientMapper,
                             SysUserMapper userMapper,
                             ExamNoGenerator noGenerator,
                             MessageService messageService) {
        this.reportMapper = reportMapper;
        this.itemMapper = itemMapper;
        this.applyMapper = applyMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
        this.messageService = messageService;
    }

    /**
     * 录入（或更新草稿）报告。
     *
     * <p>录入阶段不改变申请单状态：草稿还没发布，临床不该看到「已出报告」。
     * 申请单要等审核发布后才推进到 3。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(ExamResultRequest request) {
        BizExamApply apply = applyMapper.selectById(request.getApplyId());
        if (apply == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "检查单不存在");
        }
        if (Integer.valueOf(APPLY_REPORTED).equals(apply.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该检查单已出报告");
        }
        if (apply.getStatus() < APPLY_WAITING_SAMPLE) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该检查单尚未就绪，不能录入报告");
        }

        BizExamReport existing = reportMapper.selectByApplyId(apply.getId());
        if (existing != null && Integer.valueOf(REPORT_PUBLISHED).equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "报告已发布，不允许修改");
        }

        // 只允许回填本检查单下的明细，防止把别单的结果写进来
        Set<Long> validItemIds = itemMapper.selectList(Wrappers.<BizExamItem>lambdaQuery()
                        .eq(BizExamItem::getApplyId, apply.getId()))
                .stream().map(BizExamItem::getId).collect(Collectors.toCollection(HashSet::new));

        Long reporterId = CurrentUser.requireId();
        BizExamReport report = existing;
        if (report == null) {
            report = new BizExamReport();
            report.setApplyId(apply.getId());
            report.setReportNo(noGenerator.nextReportNo());
            report.setReporterId(reporterId);
            report.setStatus(REPORT_DRAFT);
            report.setIsCritical(normalizeCritical(request.getIsCritical()));
            report.setResult(request.getResult());
            report.setConclusion(request.getConclusion());
            reportMapper.insert(report);
        } else {
            report.setIsCritical(normalizeCritical(request.getIsCritical()));
            report.setResult(request.getResult());
            report.setConclusion(request.getConclusion());
            report.setReporterId(reporterId);
            reportMapper.updateById(report);
        }

        for (ExamResultItemRequest itemRequest : request.getItems()) {
            if (!validItemIds.contains(itemRequest.getItemId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "明细 " + itemRequest.getItemId() + " 不属于该检查单");
            }
            BizExamItem update = new BizExamItem();
            update.setId(itemRequest.getItemId());
            update.setResultValue(itemRequest.getResultValue());
            update.setRefRange(itemRequest.getRefRange());
            update.setUnit(itemRequest.getUnit());
            update.setAbnormalFlag(itemRequest.getAbnormalFlag() == null ? 0 : itemRequest.getAbnormalFlag());
            itemMapper.updateById(update);
        }

        log.info("报告录入成功 reportNo={} applyId={} critical={}",
                report.getReportNo(), apply.getId(), report.getIsCritical());
        return report.getId();
    }

    /**
     * 审核发布：报告 0 → 1，同时把申请单推进到「已出报告」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long reportId) {
        BizExamReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "报告不存在");
        }
        Long operatorId = CurrentUser.requireId();
        if (!isAdmin() && operatorId.equals(report.getReporterId())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "不能审核自己录入的报告");
        }

        if (reportMapper.transitStatus(reportId, REPORT_DRAFT, REPORT_PUBLISHED, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "报告状态已变化，请刷新后重试");
        }

        BizExamApply apply = applyMapper.selectById(report.getApplyId());
        if (apply != null && apply.getStatus() < APPLY_REPORTED) {
            // 可能是 1（未采样直接出报告）或 2（检验中）
            applyMapper.transitStatus(apply.getId(), apply.getStatus(), APPLY_REPORTED, operatorId);
        }

        if (Integer.valueOf(1).equals(report.getIsCritical())) {
            notifyCritical(report);
        }
    }

    /**
     * 危急值发布后立刻通知申请医生。
     *
     * <p>只靠「临床自己去刷列表」发现危急值是不够的 ——
     * 危急值的定义就是「不立即处理会有生命危险」，
     * 所以这里主动推一条待办消息，把闭环的起点交到临床手上。</p>
     *
     * <p>发消息失败不能让发布回滚：报告已经审核通过，
     * 因为通知渠道抖了一下就把已发布报告撤回去，是把小故障放大成大事故。</p>
     */
    private void notifyCritical(BizExamReport report) {
        BizExamApply apply = applyMapper.selectById(report.getApplyId());
        if (apply == null || apply.getDoctorId() == null) {
            log.warn("危急值报告缺少申请医生，跳过通知 reportNo={}", report.getReportNo());
            return;
        }
        try {
            messageService.sendTo(apply.getDoctorId(),
                    "【危急值】" + (apply.getExamItem() == null ? "检查报告" : apply.getExamItem()) + " 需立即处理",
                    "报告号 " + report.getReportNo() + " 已发布并标记为危急值，请尽快查看并采取处理措施。",
                    MessageService.TYPE_WARNING, "exam", report.getId());
        } catch (Exception e) {
            log.error("危急值通知发送失败 reportNo={} doctorId={}", report.getReportNo(), apply.getDoctorId(), e);
        }
    }

    public ExamReportVO getByApply(Long applyId) {
        BizExamReport report = reportMapper.selectByApplyId(applyId);
        return report == null ? null : toVO(report, loadContext(List.of(report)));
    }

    public ExamReportVO detail(Long id) {
        BizExamReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "报告不存在");
        }
        return toVO(report, loadContext(List.of(report)));
    }

    /**
     * 危急值处理：临床医生看到危急值后登记处理措施，闭环。
     *
     * <p><b>只有已发布的危急值报告才能处理</b>：草稿阶段的异常结果还没复核，
     * 让临床照着草稿去抢救用药，是把未审核数据当结论用。</p>
     *
     * <p>处理措施必填 —— 危急值闭环的核心是「做了什么」，
     * 只勾一个「已处理」等于什么都没留下。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleCritical(Long reportId, String remark) {
        BizExamReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "报告不存在");
        }
        if (!Integer.valueOf(1).equals(report.getIsCritical())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该报告不是危急值，无需处理");
        }
        if (!Integer.valueOf(REPORT_PUBLISHED).equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "报告尚未发布，不能登记处理");
        }
        if (Integer.valueOf(1).equals(report.getHandleStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该危急值已处理，请勿重复登记");
        }
        if (!StringUtils.hasText(remark)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "处理措施不能为空");
        }

        Long operatorId = CurrentUser.requireId();
        if (reportMapper.handleCritical(reportId, operatorId, remark.trim()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "危急值状态已变化，请刷新后重试");
        }
        log.info("危急值已处理 reportNo={} handler={}", report.getReportNo(), operatorId);
    }

    /**
     * 危急值待处理列表：已发布且标记为危急值的报告。
     *
     * <p>这个列表是临床的「必须看一眼」队列，所以只查 status=1，
     * 草稿状态的危急值不算 —— 还没审核完的结果不能惊动临床。</p>
     */
    public PageResult<ExamReportVO> pageCritical(ExamApplyQuery query) {
        LambdaQueryWrapper<BizExamReport> wrapper = Wrappers.<BizExamReport>lambdaQuery()
                .eq(BizExamReport::getIsCritical, 1)
                .eq(BizExamReport::getStatus, REPORT_PUBLISHED);

        if (query.getStartTime() != null) {
            wrapper.ge(BizExamReport::getReportTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizExamReport::getReportTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizExamReport::getReportTime);

        Page<BizExamReport> page = reportMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Context context = loadContext(page.getRecords());
        return PageResult.of(page, report -> toVO(report, context));
    }

    /**
     * 报告分页查询（报告查询页用）。
     *
     * <p>患者名是跨表条件，不能直接拼进报告表的 WHERE ——
     * 先按姓名查出患者 → 再查出这些患者的申请单 → 最后用 {@code apply_id IN} 收窄。
     * 三步查询看着绕，但每一步结果集都有限，比在 SQL 里 join 三张表更好维护，
     * 也避开了 MyBatis-Plus 拼 join 时列名冲突的坑。</p>
     */
    public PageResult<ExamReportVO> page(ExamReportQuery query) {
        LambdaQueryWrapper<BizExamReport> wrapper = Wrappers.<BizExamReport>lambdaQuery()
                .eq(query.getStatus() != null, BizExamReport::getStatus, query.getStatus())
                .eq(query.getIsCritical() != null, BizExamReport::getIsCritical, query.getIsCritical());

        if (StringUtils.hasText(query.getReportNo())) {
            wrapper.likeRight(BizExamReport::getReportNo, query.getReportNo().trim());
        }
        if (Boolean.TRUE.equals(query.getPendingCritical())) {
            wrapper.eq(BizExamReport::getIsCritical, 1)
                    .eq(BizExamReport::getStatus, REPORT_PUBLISHED)
                    .eq(BizExamReport::getHandleStatus, 0);
        }
        if (query.getStartTime() != null) {
            wrapper.ge(BizExamReport::getReportTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizExamReport::getReportTime, query.getEndTime().plusDays(1).atStartOfDay());
        }

        if (StringUtils.hasText(query.getPatientName())) {
            List<Long> patientIds = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .likeRight(BizPatient::getName, query.getPatientName().trim()))
                    .stream().map(BizPatient::getId).toList();
            if (patientIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            List<Long> applyIds = applyMapper.selectList(Wrappers.<BizExamApply>lambdaQuery()
                            .in(BizExamApply::getPatientId, patientIds))
                    .stream().map(BizExamApply::getId).toList();
            if (applyIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(BizExamReport::getApplyId, applyIds);
        }

        wrapper.orderByDesc(BizExamReport::getReportTime);
        Page<BizExamReport> page = reportMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Context context = loadContext(page.getRecords());
        return PageResult.of(page, report -> toVO(report, context));
    }

    private Integer normalizeCritical(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private boolean isAdmin() {
        LoginUser user = CurrentUser.orNull();
        return user != null && user.getRoleCodes().contains(ADMIN_ROLE);
    }

    private record Context(Map<Long, BizExamApply> applies,
                           Map<Long, List<ExamItemVO>> items,
                           Map<Long, String> patients,
                           Map<Long, String> users) {
    }

    private Context loadContext(List<BizExamReport> reports) {
        if (reports.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<Long> applyIds = reports.stream().map(BizExamReport::getApplyId).collect(Collectors.toSet());
        List<BizExamApply> applies = applyMapper.selectByIds(applyIds);
        Map<Long, BizExamApply> applyMap = applies.stream()
                .collect(Collectors.toMap(BizExamApply::getId, a -> a));

        List<BizExamItem> items = itemMapper.selectList(Wrappers.<BizExamItem>lambdaQuery()
                .in(BizExamItem::getApplyId, applyIds)
                .orderByAsc(BizExamItem::getSort));
        Map<Long, List<ExamItemVO>> itemMap = new HashMap<>();
        for (BizExamItem item : items) {
            itemMap.computeIfAbsent(item.getApplyId(), k -> new ArrayList<>())
                    .add(ExamItemVO.builder()
                            .id(String.valueOf(item.getId()))
                            .itemCode(item.getItemCode())
                            .itemName(item.getItemName())
                            .resultValue(item.getResultValue())
                            .refRange(item.getRefRange())
                            .unit(item.getUnit())
                            .abnormalFlag(item.getAbnormalFlag())
                            .sort(item.getSort())
                            .build());
        }

        Set<Long> patientIds = applies.stream().map(BizExamApply::getPatientId).collect(Collectors.toSet());
        Map<Long, String> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patients.put(p.getId(), p.getName());
            }
        }

        Set<Long> userIds = new HashSet<>();
        reports.forEach(r -> {
            if (r.getReporterId() != null) {
                userIds.add(r.getReporterId());
            }
            if (r.getAuditorId() != null) {
                userIds.add(r.getAuditorId());
            }
            if (r.getHandlerId() != null) {
                userIds.add(r.getHandlerId());
            }
        });
        Map<Long, String> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(userIds)) {
                users.put(u.getId(), u.getRealName());
            }
        }
        return new Context(applyMap, itemMap, patients, users);
    }

    private ExamReportVO toVO(BizExamReport report, Context context) {
        BizExamApply apply = context.applies().get(report.getApplyId());
        return ExamReportVO.builder()
                .id(String.valueOf(report.getId()))
                .applyId(String.valueOf(report.getApplyId()))
                .applyNo(apply == null ? null : apply.getApplyNo())
                .reportNo(report.getReportNo())
                .patientId(apply == null ? null : apply.getPatientId())
                .patientName(apply == null ? null : context.patients().get(apply.getPatientId()))
                .result(report.getResult())
                .conclusion(report.getConclusion())
                .isCritical(report.getIsCritical())
                .reporterId(report.getReporterId())
                .reporterName(context.users().get(report.getReporterId()))
                .auditorId(report.getAuditorId())
                .auditorName(context.users().get(report.getAuditorId()))
                .reportTime(report.getReportTime())
                .status(report.getStatus())
                .items(apply == null ? List.of() : context.items().getOrDefault(apply.getId(), List.of()))
                .handleStatus(report.getHandleStatus())
                .handlerName(context.users().get(report.getHandlerId()))
                .handleTime(report.getHandleTime())
                .handleRemark(report.getHandleRemark())
                .build();
    }
}
