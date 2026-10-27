package com.hospital.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import org.springframework.util.StringUtils;
import com.hospital.exam.dto.ExamItemResultRequest;
import com.hospital.exam.dto.ExamBarcodeVO;
import com.hospital.exam.dto.ExamApplyQuery;
import com.hospital.exam.dto.ExamApplyRequest;
import com.hospital.exam.dto.ExamApplyVO;
import com.hospital.exam.dto.ExamItemRequest;
import com.hospital.exam.dto.ExamItemVO;
import com.hospital.exam.entity.BizExamApply;
import com.hospital.exam.entity.BizExamItem;
import com.hospital.exam.entity.BizExamReport;
import com.hospital.exam.mapper.BizExamApplyMapper;
import com.hospital.exam.mapper.BizExamItemMapper;
import com.hospital.exam.mapper.BizExamReportMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 检查检验申请服务：开单、查询、采样。
 *
 * <p>开单同样要校验数据权限：非管理员只能给自己接诊的患者开单，
 * 否则任何登录用户都能用别人的名字开检查（直接产生费用）。</p>
 */
@Slf4j
@Service
public class ExamApplyService {

    /** 0 待缴费 / 1 待采样 / 2 检验中 / 3 已出报告 */
    private static final int APPLY_UNPAID = 0;
    private static final int APPLY_WAITING_SAMPLE = 1;
    private static final int APPLY_TESTING = 2;
    private static final int APPLY_REPORTED = 3;

    private static final int VISIT_CANCELLED = 3;
    private static final String ADMIN_ROLE = "admin";

    private final BizExamApplyMapper applyMapper;
    private final BizExamItemMapper itemMapper;
    private final BizExamReportMapper reportMapper;
    private final BizRegistrationMapper registrationMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final ExamNoGenerator noGenerator;

    public ExamApplyService(BizExamApplyMapper applyMapper,
                            BizExamItemMapper itemMapper,
                            BizExamReportMapper reportMapper,
                            BizRegistrationMapper registrationMapper,
                            BizPatientMapper patientMapper,
                            SysUserMapper userMapper,
                            ExamNoGenerator noGenerator) {
        this.applyMapper = applyMapper;
        this.itemMapper = itemMapper;
        this.reportMapper = reportMapper;
        this.registrationMapper = registrationMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ExamApplyRequest request) {
        BizRegistration registration = registrationMapper.selectById(request.getVisitId());
        if (registration == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "挂号记录不存在");
        }
        if (Integer.valueOf(VISIT_CANCELLED).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已退号的挂号不能开检查");
        }
        assertOwnedByCurrentDoctor(registration);

        Long operatorId = CurrentUser.requireId();
        BizExamApply apply = new BizExamApply();
        apply.setApplyNo(noGenerator.nextApplyNo());
        apply.setPatientId(registration.getPatientId());
        apply.setVisitId(registration.getId());
        // 申请医生取当前操作人：admin 代开时也记录实际操作者，便于追溯
        apply.setDoctorId(operatorId);
        apply.setExamType(request.getExamType());
        apply.setExamItem(request.getExamItem());
        apply.setBodyPart(request.getBodyPart());
        apply.setClinicalDiagnosis(request.getClinicalDiagnosis());
        // 开单只到「待缴费」：没收到钱就采样，等于医院垫付了耗材和人力。
        // 由 ChargeService.chargeExam 收完费推进到 1 待采样。
        apply.setStatus(APPLY_UNPAID);
        apply.setApplyTime(LocalDateTime.now());
        applyMapper.insert(apply);

        int sort = 0;
        for (ExamItemRequest itemRequest : request.getItems()) {
            BizExamItem item = new BizExamItem();
            item.setApplyId(apply.getId());
            item.setItemCode(itemRequest.getItemCode());
            item.setItemName(itemRequest.getItemName());
            item.setSort(itemRequest.getSort() == null ? sort++ : itemRequest.getSort());
            item.setAbnormalFlag(0);
            itemMapper.insert(item);
        }

        log.info("开检查单成功 applyNo={} visitId={} items={}", apply.getApplyNo(),
                registration.getId(), request.getItems().size());
        return apply.getId();
    }

    /** 采样 / 开始检查：1 待采样 → 2 检验中 */
    @Transactional(rollbackFor = Exception.class)
    public void startTesting(Long id) {
        BizExamApply apply = getExisting(id);
        // 未缴费的单不能采样：这是收费与检查之间唯一的一道闸门
        if (Integer.valueOf(APPLY_UNPAID).equals(apply.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该检查单尚未缴费，请先到收费窗口缴费");
        }
        if (Integer.valueOf(APPLY_TESTING).equals(apply.getStatus())
                || Integer.valueOf(APPLY_REPORTED).equals(apply.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该检查单已在检验中或已出报告");
        }
        if (applyMapper.transitStatus(id, apply.getStatus(), APPLY_TESTING, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "检查单状态已变化，请刷新后重试");
        }
    }

    /**
     * 样本采集：与「开始检验」同一个动作，但额外分配条码号。
     *
     * <p>条码在<b>采样时</b>生成而不是开单时：开单后患者可能不来、可能退费，
     * 提前生成条码会让条码号出现空洞，盘点时说不清哪一个管子没用。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public String sample(Long id) {
        startTesting(id);
        BizExamApply apply = getExisting(id);
        if (StringUtils.hasText(apply.getBarcodeNo())) {
            return apply.getBarcodeNo();
        }
        String barcode = "SMP" + apply.getApplyNo();
        BizExamApply update = new BizExamApply();
        update.setId(id);
        update.setBarcodeNo(barcode);
        applyMapper.updateById(update);
        log.info("样本采集 applyNo={} barcode={}", apply.getApplyNo(), barcode);
        return barcode;
    }

    /**
     * 条码信息（打印用）。
     *
     * <p>条码号<b>幂等</b>：第一次打印时生成，重复打印返回同一个号。
     * 同一管血印出两个不同条码，等于让标本在传递链上有了两个身份。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamBarcodeVO barcode(Long id) {
        BizExamApply apply = getExisting(id);
        if (!StringUtils.hasText(apply.getBarcodeNo())) {
            BizExamApply update = new BizExamApply();
            update.setId(id);
            update.setBarcodeNo("SMP" + apply.getApplyNo());
            applyMapper.updateById(update);
            apply.setBarcodeNo(update.getBarcodeNo());
        }
        BizPatient patient = patientMapper.selectById(apply.getPatientId());
        List<ExamItemVO> items = itemMapper.selectList(Wrappers.<BizExamItem>lambdaQuery()
                        .eq(BizExamItem::getApplyId, id)
                        .orderByAsc(BizExamItem::getSort))
                .stream().map(ExamApplyService::toItemVO).toList();

        return ExamBarcodeVO.builder()
                .applyId(String.valueOf(apply.getId()))
                .applyNo(apply.getApplyNo())
                .barcodeNo(apply.getBarcodeNo())
                .patientId(String.valueOf(apply.getPatientId()))
                .patientName(patient == null ? null : patient.getName())
                .examType(apply.getExamType())
                .examItem(apply.getExamItem())
                .bodyPart(apply.getBodyPart())
                .itemNames(items.stream().map(ExamItemVO::getItemName).toList())
                .build();
    }

    /** 申请单下的项目明细（含结果），结果录入页面用它做表单骨架 */
    public List<ExamItemVO> items(Long applyId) {
        getExisting(applyId);
        return itemMapper.selectList(Wrappers.<BizExamItem>lambdaQuery()
                        .eq(BizExamItem::getApplyId, applyId)
                        .orderByAsc(BizExamItem::getSort))
                .stream().map(ExamApplyService::toItemVO).toList();
    }

    /**
     * 批量保存项目结果（只改结果字段，不碰报告主表）。
     *
     * <p>与「录入报告」分开是因为这两件事的操作人可能不同：
     * 技师先把各项数值填进去，审核医师再写结论并发布。
     * 合成一个接口会让「谁改了数值、谁写的结论」分不清。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long applyId, List<ExamItemResultRequest> requests) {
        BizExamApply apply = getExisting(applyId);
        if (Integer.valueOf(APPLY_UNPAID).equals(apply.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该检查单尚未缴费");
        }
        BizExamReport report = reportMapper.selectByApplyId(applyId);
        if (report != null && Integer.valueOf(1).equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "报告已发布，不能修改结果");
        }

        Set<Long> owned = new HashSet<>();
        for (BizExamItem item : itemMapper.selectList(Wrappers.<BizExamItem>lambdaQuery()
                .eq(BizExamItem::getApplyId, applyId))) {
            owned.add(item.getId());
        }
        for (ExamItemResultRequest r : requests) {
            if (!owned.contains(r.getItemId())) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "明细 %s 不属于该检查单", r.getItemId());
            }
            BizExamItem update = new BizExamItem();
            update.setId(r.getItemId());
            update.setResultValue(r.getResultValue());
            update.setRefRange(r.getRefRange());
            update.setUnit(r.getUnit());
            update.setAbnormalFlag(r.getAbnormalFlag() == null ? 0 : r.getAbnormalFlag());
            itemMapper.updateById(update);
        }
        log.info("批量保存项目结果 applyId={} 条数={}", applyId, requests.size());
    }

    public PageResult<ExamApplyVO> page(ExamApplyQuery query) {
        LambdaQueryWrapper<BizExamApply> wrapper = Wrappers.<BizExamApply>lambdaQuery()
                .eq(query.getPatientId() != null, BizExamApply::getPatientId, query.getPatientId())
                .eq(query.getVisitId() != null, BizExamApply::getVisitId, query.getVisitId())
                .eq(query.getDoctorId() != null, BizExamApply::getDoctorId, query.getDoctorId())
                .eq(query.getExamType() != null, BizExamApply::getExamType, query.getExamType())
                .eq(query.getStatus() != null, BizExamApply::getStatus, query.getStatus());

        if (query.getStartTime() != null) {
            wrapper.ge(BizExamApply::getApplyTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizExamApply::getApplyTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizExamApply::getApplyTime);

        Page<BizExamApply> page = applyMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Assembled assembled = assemble(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, assembled));
    }

    public ExamApplyVO detail(Long id) {
        BizExamApply apply = getExisting(id);
        return toVO(apply, assemble(List.of(apply)));
    }

    private static ExamItemVO toItemVO(BizExamItem item) {
        return ExamItemVO.builder()
                .id(String.valueOf(item.getId()))
                .itemCode(item.getItemCode())
                .itemName(item.getItemName())
                .resultValue(item.getResultValue())
                .refRange(item.getRefRange())
                .unit(item.getUnit())
                .abnormalFlag(item.getAbnormalFlag())
                .sort(item.getSort())
                .build();
    }

    private BizExamApply getExisting(Long id) {
        BizExamApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "检查单不存在或已删除");
        }
        return apply;
    }

    /** 一次性把明细、患者名、医生名、报告摘要都装配好，避免逐行查询 */
    private record Assembled(Map<Long, List<ExamItemVO>> items,
                             Map<Long, BizExamReport> reports,
                             Map<Long, String> patients,
                             Map<Long, String> doctors) {
    }

    private Assembled assemble(List<BizExamApply> applies) {
        if (applies.isEmpty()) {
            return new Assembled(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<Long> applyIds = applies.stream().map(BizExamApply::getId).collect(Collectors.toSet());
        Set<Long> patientIds = applies.stream().map(BizExamApply::getPatientId).collect(Collectors.toSet());
        Set<Long> doctorIds = applies.stream().map(BizExamApply::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

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

        List<BizExamReport> reports = reportMapper.selectList(Wrappers.<BizExamReport>lambdaQuery()
                .in(BizExamReport::getApplyId, applyIds));
        Map<Long, BizExamReport> reportMap = new HashMap<>();
        for (BizExamReport report : reports) {
            reportMap.put(report.getApplyId(), report);
        }

        Map<Long, String> patients = new HashMap<>();
        for (BizPatient p : patientMapper.selectByIds(patientIds)) {
            patients.put(p.getId(), p.getName());
        }
        Map<Long, String> doctors = new HashMap<>();
        if (!doctorIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(doctorIds)) {
                doctors.put(u.getId(), u.getRealName());
            }
        }
        return new Assembled(itemMap, reportMap, patients, doctors);
    }

    private ExamApplyVO toVO(BizExamApply entity, Assembled assembled) {
        BizExamReport report = assembled.reports().get(entity.getId());
        return ExamApplyVO.builder()
                .id(String.valueOf(entity.getId()))
                .applyNo(entity.getApplyNo())
                .patientId(entity.getPatientId())
                .patientName(assembled.patients().get(entity.getPatientId()))
                .visitId(entity.getVisitId())
                .doctorId(entity.getDoctorId())
                .doctorName(assembled.doctors().get(entity.getDoctorId()))
                .examType(entity.getExamType())
                .examItem(entity.getExamItem())
                .bodyPart(entity.getBodyPart())
                .clinicalDiagnosis(entity.getClinicalDiagnosis())
                .status(entity.getStatus())
                .barcodeNo(entity.getBarcodeNo())
                .applyTime(entity.getApplyTime())
                .items(assembled.items().getOrDefault(entity.getId(), List.of()))
                .reportId(report == null ? null : String.valueOf(report.getId()))
                .reportStatus(report == null ? null : report.getStatus())
                .isCritical(report == null ? null : report.getIsCritical())
                .build();
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
            throw new BusinessException(ErrorCode.DATA_FORBIDDEN, "只能为自己接诊的患者开检查");
        }
    }
}
