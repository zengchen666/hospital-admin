package com.hospital.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.clinic.dto.PrescriptionItemRequest;
import com.hospital.clinic.dto.PrescriptionItemVO;
import com.hospital.clinic.dto.PrescriptionQuery;
import com.hospital.clinic.dto.PrescriptionRequest;
import com.hospital.clinic.dto.PrescriptionVO;
import com.hospital.clinic.entity.BizPrescription;
import com.hospital.clinic.entity.BizPrescriptionItem;
import com.hospital.clinic.mapper.BizPrescriptionItemMapper;
import com.hospital.clinic.mapper.BizPrescriptionMapper;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处方服务：开方、审核、作废、查询。
 *
 * <p><b>三条不能让步的规则</b>：</p>
 * <ol>
 *   <li><b>金额只信服务端</b>：单价取药品字典的 {@code sale_price}，
 *       小计与总额服务端算。前端传价格等于把定价权交给了浏览器。</li>
 *   <li><b>药品信息做快照</b>：明细里存名称/规格/单价，药品后改名调价不影响历史处方。</li>
 *   <li><b>状态流转用条件更新</b>：审核与作废都带「原状态」条件，
 *       并发下只有一个能成功，防止「已发药的处方被作废」。</li>
 * </ol>
 */
@Slf4j
@Service
public class PrescriptionService {

    private static final int STATUS_PENDING_AUDIT = 0;
    private static final int STATUS_AUDITED = 1;
    private static final int STATUS_CHARGED = 2;
    private static final int STATUS_DISPENSED = 3;
    private static final int STATUS_CANCELLED = 4;

    private static final int VISIT_WAITING = 0;
    private static final int VISIT_IN_PROGRESS = 1;
    private static final int VISIT_FINISHED = 2;
    private static final int VISIT_CANCELLED = 3;

    private static final String ADMIN_ROLE = "admin";

    private final BizPrescriptionMapper prescriptionMapper;
    private final BizPrescriptionItemMapper itemMapper;
    private final BizRegistrationMapper registrationMapper;
    private final BizPatientMapper patientMapper;
    private final BizDrugMapper drugMapper;
    private final SysUserMapper userMapper;
    private final PrescNoGenerator noGenerator;

    public PrescriptionService(BizPrescriptionMapper prescriptionMapper,
                               BizPrescriptionItemMapper itemMapper,
                               BizRegistrationMapper registrationMapper,
                               BizPatientMapper patientMapper,
                               BizDrugMapper drugMapper,
                               SysUserMapper userMapper,
                               PrescNoGenerator noGenerator) {
        this.prescriptionMapper = prescriptionMapper;
        this.itemMapper = itemMapper;
        this.registrationMapper = registrationMapper;
        this.patientMapper = patientMapper;
        this.drugMapper = drugMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PrescriptionRequest request) {
        BizRegistration registration = registrationMapper.selectById(request.getVisitId());
        if (registration == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "挂号记录不存在");
        }
        if (Integer.valueOf(VISIT_CANCELLED).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已退号的挂号不能开方");
        }
        if (Integer.valueOf(VISIT_WAITING).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "请先接诊再开方");
        }
        if (Integer.valueOf(VISIT_FINISHED).equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该次就诊已完成，无法继续开方");
        }
        assertOwnedByCurrentDoctor(registration);

        // 一次性把涉及到的药品查出来，避免循环查库
        Set<Long> drugIds = request.getItems().stream()
                .map(PrescriptionItemRequest::getDrugId)
                .collect(Collectors.toSet());
        Map<Long, BizDrug> drugMap = new HashMap<>();
        for (BizDrug drug : drugMapper.selectByIds(drugIds)) {
            drugMap.put(drug.getId(), drug);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<BizPrescriptionItem> itemEntities = new ArrayList<>(request.getItems().size());
        for (PrescriptionItemRequest item : request.getItems()) {
            BizDrug drug = drugMap.get(item.getDrugId());
            if (drug == null) {
                throw new BusinessException(ErrorCode.NO_RESOURCE, "药品不存在，drugId=" + item.getDrugId());
            }
            if (!Integer.valueOf(1).equals(drug.getStatus())) {
                throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                        "药品「" + drug.getDrugName() + "」已停用，不能开具");
            }

            // 单价一律取库里的售价，前端无从干预
            BigDecimal price = drug.getSalePrice() == null ? BigDecimal.ZERO : drug.getSalePrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(amount);

            BizPrescriptionItem entity = new BizPrescriptionItem();
            entity.setDrugId(drug.getId());
            entity.setDrugName(drug.getDrugName());
            entity.setSpec(drug.getSpec());
            entity.setUsage(item.getUsage());
            entity.setDosage(item.getDosage());
            entity.setFrequency(item.getFrequency());
            entity.setDays(item.getDays());
            entity.setQuantity(item.getQuantity());
            entity.setPrice(price);
            entity.setAmount(amount);
            itemEntities.add(entity);
        }

        BizPrescription prescription = new BizPrescription();
        prescription.setPrescNo(noGenerator.next());
        prescription.setPatientId(registration.getPatientId());
        // 开方医生取挂号上的接诊医生，不采用前端传值
        prescription.setDoctorId(registration.getDoctorId());
        prescription.setVisitId(registration.getId());
        prescription.setPrescType(request.getPrescType());
        prescription.setDiagnosis(request.getDiagnosis());
        prescription.setTotalAmount(total);
        prescription.setStatus(STATUS_PENDING_AUDIT);
        prescriptionMapper.insert(prescription);

        for (BizPrescriptionItem entity : itemEntities) {
            entity.setPrescId(prescription.getId());
            itemMapper.insert(entity);
        }

        log.info("开方成功 prescNo={} visitId={} items={} total={}",
                prescription.getPrescNo(), registration.getId(), itemEntities.size(), total);
        return prescription.getId();
    }

    /**
     * 处方审核：0 待审核 → 1 已审核。
     *
     * <p><b>不能自审</b>：开方医生不该同时是审核人，否则审核环节形同虚设。
     * admin 角色豁免（系统管理员默认承担全部职责，生产环境应拆出独立药师角色）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        BizPrescription prescription = getExisting(id);
        Long operatorId = CurrentUser.requireId();

        if (!isAdmin() && operatorId.equals(prescription.getDoctorId())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "不能审核自己开具的处方");
        }

        int affected = prescriptionMapper.transitStatus(id, STATUS_PENDING_AUDIT, STATUS_AUDITED, operatorId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "处方状态已变化，请刷新后重试");
        }
        log.info("处方审核通过 prescNo={} auditor={}", prescription.getPrescNo(), operatorId);
    }

    /**
     * 作废处方：仅限「待审核 / 已审核」。
     *
     * <p>已收费或已发药不能直接作废 —— 钱收了、药出库了，必须走退费退药流程。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        BizPrescription prescription = getExisting(id);
        Integer current = prescription.getStatus();
        if (Integer.valueOf(STATUS_CHARGED).equals(current) || Integer.valueOf(STATUS_DISPENSED).equals(current)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                    "处方已收费或已发药，不能直接作废，请走退费流程");
        }
        if (Integer.valueOf(STATUS_CANCELLED).equals(current)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方已作废，请勿重复操作");
        }

        int affected = prescriptionMapper.transitStatus(id, current, STATUS_CANCELLED, CurrentUser.idOrNull());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "处方状态已变化，请刷新后重试");
        }
        log.info("处方作废 prescNo={} from={}", prescription.getPrescNo(), current);
    }

    public PageResult<PrescriptionVO> page(PrescriptionQuery query) {
        LambdaQueryWrapper<BizPrescription> wrapper = Wrappers.<BizPrescription>lambdaQuery()
                .eq(query.getVisitId() != null, BizPrescription::getVisitId, query.getVisitId())
                .eq(query.getPatientId() != null, BizPrescription::getPatientId, query.getPatientId())
                .eq(query.getDoctorId() != null, BizPrescription::getDoctorId, query.getDoctorId())
                .eq(query.getStatus() != null, BizPrescription::getStatus, query.getStatus())
                .eq(query.getPrescType() != null, BizPrescription::getPrescType, query.getPrescType());

        if (query.getStartTime() != null) {
            wrapper.ge(BizPrescription::getCreateTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizPrescription::getCreateTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizPrescription::getCreateTime);

        Page<BizPrescription> page = prescriptionMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        // 明细与姓名都批量装配，一页 20 张处方只查 3 次（明细 + 患者 + 医生）
        Map<Long, List<PrescriptionItemVO>> itemMap = loadItems(page.getRecords());
        Names names = loadNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, itemMap, names));
    }

    public PrescriptionVO detail(Long id) {
        BizPrescription entity = getExisting(id);
        Map<Long, List<PrescriptionItemVO>> itemMap = loadItems(List.of(entity));
        Names names = loadNames(List.of(entity));
        return toVO(entity, itemMap, names);
    }

    private BizPrescription getExisting(Long id) {
        BizPrescription entity = prescriptionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "处方不存在或已删除");
        }
        return entity;
    }

    private Map<Long, List<PrescriptionItemVO>> loadItems(List<BizPrescription> prescriptions) {
        if (prescriptions.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = prescriptions.stream().map(BizPrescription::getId).collect(Collectors.toSet());
        List<BizPrescriptionItem> items = itemMapper.selectList(
                Wrappers.<BizPrescriptionItem>lambdaQuery().in(BizPrescriptionItem::getPrescId, ids));

        Map<Long, List<PrescriptionItemVO>> result = new HashMap<>();
        for (BizPrescriptionItem item : items) {
            result.computeIfAbsent(item.getPrescId(), k -> new ArrayList<>()).add(toItemVO(item));
        }
        return result;
    }

    private PrescriptionItemVO toItemVO(BizPrescriptionItem item) {
        return PrescriptionItemVO.builder()
                .id(String.valueOf(item.getId()))
                .drugId(item.getDrugId())
                .drugName(item.getDrugName())
                .spec(item.getSpec())
                .usage(item.getUsage())
                .dosage(item.getDosage())
                .frequency(item.getFrequency())
                .days(item.getDays())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .amount(item.getAmount())
                .build();
    }

    private record Names(Map<Long, String> patients, Map<Long, String> doctors) {
    }

    private Names loadNames(List<BizPrescription> prescriptions) {
        Set<Long> patientIds = prescriptions.stream()
                .map(BizPrescription::getPatientId).collect(Collectors.toSet());
        Set<Long> doctorIds = prescriptions.stream()
                .map(BizPrescription::getDoctorId).collect(Collectors.toSet());

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
        return new Names(patients, doctors);
    }

    private PrescriptionVO toVO(BizPrescription entity,
                                Map<Long, List<PrescriptionItemVO>> itemMap,
                                Names names) {
        return PrescriptionVO.builder()
                .id(String.valueOf(entity.getId()))
                .prescNo(entity.getPrescNo())
                .patientId(entity.getPatientId())
                .patientName(names.patients().get(entity.getPatientId()))
                .doctorId(entity.getDoctorId())
                .doctorName(names.doctors().get(entity.getDoctorId()))
                .visitId(String.valueOf(entity.getVisitId()))
                .prescType(entity.getPrescType())
                .diagnosis(entity.getDiagnosis())
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .items(itemMap.getOrDefault(entity.getId(), List.of()))
                .build();
    }

    private boolean isAdmin() {
        LoginUser user = CurrentUser.orNull();
        return user != null && user.getRoleCodes().contains(ADMIN_ROLE);
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
            throw new BusinessException(ErrorCode.DATA_FORBIDDEN, "只能为自己接诊的患者开方");
        }
    }
}
