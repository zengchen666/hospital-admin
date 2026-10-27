package com.hospital.inpatient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.inpatient.dto.AdmissionQuery;
import com.hospital.inpatient.dto.AdmissionRequest;
import com.hospital.inpatient.dto.AdmissionVO;
import com.hospital.inpatient.dto.BedQuery;
import com.hospital.inpatient.dto.BedVO;
import com.hospital.inpatient.dto.WardVO;
import com.hospital.inpatient.entity.BizAdmission;
import com.hospital.inpatient.entity.BizBed;
import com.hospital.inpatient.entity.BizWard;
import com.hospital.inpatient.mapper.BizAdmissionMapper;
import com.hospital.inpatient.mapper.BizBedMapper;
import com.hospital.inpatient.mapper.BizWardMapper;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 住院服务：入院登记、分床、出院、预交金、床位图。
 *
 * <p><b>床位是并发焦点</b>：占用与释放都走条件更新，见 {@link BizBedMapper#occupy}。
 * 一床两人的后果不是数据难看，而是真实世界里两个患者被安排到同一张床上。</p>
 */
@Slf4j
@Service
public class AdmissionService {

    private static final int BED_FREE = 0;
    private static final int STATUS_IN_HOSPITAL = 0;
    private static final int STATUS_DISCHARGED = 1;

    private final BizAdmissionMapper admissionMapper;
    private final BizBedMapper bedMapper;
    private final BizWardMapper wardMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final AdmissionNoGenerator noGenerator;

    public AdmissionService(BizAdmissionMapper admissionMapper,
                            BizBedMapper bedMapper,
                            BizWardMapper wardMapper,
                            BizPatientMapper patientMapper,
                            SysUserMapper userMapper,
                            AdmissionNoGenerator noGenerator) {
        this.admissionMapper = admissionMapper;
        this.bedMapper = bedMapper;
        this.wardMapper = wardMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 入院登记。
     *
     * <p>顺序很重要：<b>先占床、再落住院记录</b>。反过来会出现「记录已建、床没占上」，
     * 虽然事务会回滚，但先占床能让并发冲突尽早暴露，错误信息也更准确。
     * 床位被抢时抛 40900 而不是 500，前端可以直接提示换床。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long admit(AdmissionRequest request) {
        BizPatient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在，请先建档");
        }
        if (admissionMapper.countInHospital(request.getPatientId()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该患者已在院，不能重复办理入院");
        }

        if (request.getBedId() != null) {
            occupyBed(request.getBedId());
        }

        BizAdmission admission = new BizAdmission();
        admission.setAdmissionNo(noGenerator.next());
        admission.setPatientId(request.getPatientId());
        admission.setDeptId(request.getDeptId());
        admission.setBedId(request.getBedId());
        admission.setDoctorId(request.getDoctorId());
        admission.setNurseId(request.getNurseId());
        admission.setAdmissionTime(LocalDateTime.now());
        admission.setDiagnosis(request.getDiagnosis());
        admission.setDeposit(request.getDeposit() == null ? BigDecimal.ZERO : request.getDeposit());
        admission.setStatus(STATUS_IN_HOSPITAL);
        admissionMapper.insert(admission);

        log.info("入院登记成功 admissionNo={} patientId={} bedId={}",
                admission.getAdmissionNo(), patient.getId(), request.getBedId());
        return admission.getId();
    }

    /**
     * 分床 / 换床：先占新床，再释放旧床。
     *
     * <p>顺序不能反：先释放旧床再占新床，如果新床被抢，患者就变成「没床的在院患者」。
     * 先占新床失败则整单回滚，原床不受影响。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignBed(Long admissionId, Long bedId) {
        BizAdmission admission = getInHospital(admissionId);
        if (Objects.equals(admission.getBedId(), bedId)) {
            return;
        }
        occupyBed(bedId);

        BizAdmission update = new BizAdmission();
        update.setId(admissionId);
        update.setBedId(bedId);
        admissionMapper.updateById(update);

        if (admission.getBedId() != null) {
            bedMapper.release(admission.getBedId());
        }
        log.info("分床成功 admissionId={} from={} to={}", admissionId, admission.getBedId(), bedId);
    }

    /**
     * 出院：置状态 + 写时间 + 释放床位，三件事同一事务。
     *
     * <p>漏掉释放床位，那张床会永远显示「占用」，别人住不进来 —— 这是住院系统最常见的数据事故。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void discharge(Long id) {
        BizAdmission admission = getInHospital(id);

        if (admissionMapper.discharge(id, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "住院状态已变化，请刷新后重试");
        }
        if (admission.getBedId() != null) {
            bedMapper.release(admission.getBedId());
        }
        log.info("出院成功 admissionNo={} 住院天数={}",
                admission.getAdmissionNo(),
                Duration.between(admission.getAdmissionTime(), LocalDateTime.now()).toDays());
    }

    /** 预交金充值：只增不减，退款走退费流程 */
    @Transactional(rollbackFor = Exception.class)
    public void addDeposit(Long id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "充值金额必须大于 0");
        }
        getInHospital(id);
        if (admissionMapper.addDeposit(id, amount, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "住院状态已变化，请刷新后重试");
        }
    }

    public PageResult<AdmissionVO> page(AdmissionQuery query) {
        LambdaQueryWrapper<BizAdmission> wrapper = Wrappers.<BizAdmission>lambdaQuery()
                .eq(query.getPatientId() != null, BizAdmission::getPatientId, query.getPatientId())
                .eq(query.getDeptId() != null, BizAdmission::getDeptId, query.getDeptId())
                .eq(query.getDoctorId() != null, BizAdmission::getDoctorId, query.getDoctorId())
                .eq(query.getStatus() != null, BizAdmission::getStatus, query.getStatus());

        if (query.getStartTime() != null) {
            wrapper.ge(BizAdmission::getAdmissionTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizAdmission::getAdmissionTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizAdmission::getAdmissionTime);

        Page<BizAdmission> page = admissionMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Context context = loadContext(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, context));
    }

    public AdmissionVO detail(Long id) {
        BizAdmission admission = admissionMapper.selectById(id);
        if (admission == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "住院记录不存在");
        }
        return toVO(admission, loadContext(List.of(admission)));
    }

    /** 床位图：某一病区的床位与占用情况 */
    public PageResult<BedVO> beds(BedQuery query) {
        LambdaQueryWrapper<BizBed> wrapper = Wrappers.<BizBed>lambdaQuery()
                .eq(query.getWardId() != null, BizBed::getWardId, query.getWardId())
                .eq(query.getBedType() != null, BizBed::getBedType, query.getBedType())
                .eq(query.getStatus() != null, BizBed::getStatus, query.getStatus());

        if (Boolean.TRUE.equals(query.getOnlyAvailable())) {
            wrapper.eq(BizBed::getStatus, BED_FREE);
        }
        wrapper.orderByAsc(BizBed::getWardId).orderByAsc(BizBed::getBedNo);

        Page<BizBed> page = bedMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        // 占用者姓名：按 bedId 反查在院记录，一次查完
        Set<Long> bedIds = page.getRecords().stream().map(BizBed::getId).collect(Collectors.toSet());
        Map<Long, String> occupants = new HashMap<>();
        if (!bedIds.isEmpty()) {
            List<BizAdmission> actives = admissionMapper.selectList(Wrappers.<BizAdmission>lambdaQuery()
                    .in(BizAdmission::getBedId, bedIds)
                    .eq(BizAdmission::getStatus, STATUS_IN_HOSPITAL));
            if (!actives.isEmpty()) {
                Map<Long, String> patientNames = new HashMap<>();
                Set<Long> patientIds = actives.stream()
                        .map(BizAdmission::getPatientId).collect(Collectors.toSet());
                for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                    patientNames.put(p.getId(), p.getName());
                }
                actives.forEach(a -> occupants.put(a.getBedId(), patientNames.get(a.getPatientId())));
            }
        }

        Set<Long> wardIds = page.getRecords().stream().map(BizBed::getWardId).collect(Collectors.toSet());
        Map<Long, String> wardNames = new HashMap<>();
        if (!wardIds.isEmpty()) {
            for (BizWard w : wardMapper.selectByIds(wardIds)) {
                wardNames.put(w.getId(), w.getWardName());
            }
        }

        return PageResult.of(page, bed -> BedVO.builder()
                .id(String.valueOf(bed.getId()))
                .wardId(bed.getWardId())
                .wardName(wardNames.get(bed.getWardId()))
                .bedNo(bed.getBedNo())
                .bedType(bed.getBedType())
                .price(bed.getPrice())
                .status(bed.getStatus())
                .occupantName(occupants.get(bed.getId()))
                .build());
    }

    /** 病区列表：床位图与入院登记的下拉框都用它，只返回启用中的病区 */
    public List<WardVO> wards() {
        List<BizWard> wards = wardMapper.selectList(Wrappers.<BizWard>lambdaQuery()
                .eq(BizWard::getStatus, 1)
                .orderByAsc(BizWard::getWardCode));
        return wards.stream().map(w -> WardVO.builder()
                .id(w.getId())
                .wardName(w.getWardName())
                .wardCode(w.getWardCode())
                .deptId(w.getDeptId())
                .floor(w.getFloor())
                .bedCount(w.getBedCount())
                .status(w.getStatus())
                .build()).toList();
    }

    /**
     * 可分配床位：某病区下所有空闲床位。
     *
     * <p>不分页：一个病区最多几十张床，分页反而让分床弹窗多一次交互。
     * 真到了几百张床的规模，那时候该做的是按楼层分组，不是分页。</p>
     */
    public List<BedVO> availableBeds(Long wardId) {
        LambdaQueryWrapper<BizBed> wrapper = Wrappers.<BizBed>lambdaQuery()
                .eq(wardId != null, BizBed::getWardId, wardId)
                .eq(BizBed::getStatus, BED_FREE)
                .orderByAsc(BizBed::getBedNo);
        List<BizBed> beds = bedMapper.selectList(wrapper);
        if (beds.isEmpty()) {
            return List.of();
        }
        Set<Long> wardIds = beds.stream().map(BizBed::getWardId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> wardNames = new HashMap<>();
        for (BizWard w : wardMapper.selectByIds(wardIds)) {
            wardNames.put(w.getId(), w.getWardName());
        }
        return beds.stream().map(bed -> BedVO.builder()
                .id(String.valueOf(bed.getId()))
                .wardId(bed.getWardId())
                .wardName(wardNames.get(bed.getWardId()))
                .bedNo(bed.getBedNo())
                .bedType(bed.getBedType())
                .price(bed.getPrice())
                .status(bed.getStatus())
                .build()).toList();
    }

    /**
     * 转科：换科室、换医生，并释放原床位。
     *
     * <p>释放床位是因为新科室的床位由对方护士重新分配，不能带着旧病区的床走。
     * 这里状态仍然置 0（在院）—— 转科不是出院，住院号连续才有意义。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, Long deptId, Long doctorId) {
        BizAdmission admission = getInHospital(id);
        if (deptId == null && doctorId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标科室与接收医生至少填一项");
        }

        BizAdmission update = new BizAdmission();
        update.setId(id);
        update.setDeptId(deptId == null ? admission.getDeptId() : deptId);
        update.setDoctorId(doctorId == null ? admission.getDoctorId() : doctorId);
        // 换了病区，原床位必须还回去
        update.setBedId(null);
        if (admissionMapper.transfer(id, update.getDeptId(), update.getDoctorId(), CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "住院状态已变化，请刷新后重试");
        }
        if (admission.getBedId() != null) {
            bedMapper.release(admission.getBedId());
        }
        log.info("转科成功 admissionNo={} fromDept={} toDept={}",
                admission.getAdmissionNo(), admission.getDeptId(), update.getDeptId());
    }

    private void occupyBed(Long bedId) {
        BizBed bed = bedMapper.selectById(bedId);
        if (bed == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "床位不存在");
        }
        // 条件更新：只有空闲才能占上
        if (bedMapper.occupy(bedId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "床位 " + bed.getBedNo() + " 已被占用，请选择其他床位");
        }
    }

    private BizAdmission getInHospital(Long id) {
        BizAdmission admission = admissionMapper.selectById(id);
        if (admission == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "住院记录不存在");
        }
        if (!Integer.valueOf(STATUS_IN_HOSPITAL).equals(admission.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED,
                    Integer.valueOf(STATUS_DISCHARGED).equals(admission.getStatus())
                            ? "该患者已出院" : "该住院记录状态不允许此操作");
        }
        return admission;
    }

    private record Context(Map<Long, String> patients, Map<Long, String> doctors,
                           Map<Long, BizBed> beds, Map<Long, BizWard> wards) {
    }

    private Context loadContext(List<BizAdmission> admissions) {
        if (admissions.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<Long> patientIds = admissions.stream().map(BizAdmission::getPatientId).collect(Collectors.toSet());
        Set<Long> doctorIds = admissions.stream().map(BizAdmission::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> bedIds = admissions.stream().map(BizAdmission::getBedId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

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
        Map<Long, BizBed> beds = new HashMap<>();
        if (!bedIds.isEmpty()) {
            for (BizBed b : bedMapper.selectByIds(bedIds)) {
                beds.put(b.getId(), b);
            }
        }
        Set<Long> wardIds = beds.values().stream().map(BizBed::getWardId).collect(Collectors.toSet());
        Map<Long, BizWard> wards = new HashMap<>();
        if (!wardIds.isEmpty()) {
            for (BizWard w : wardMapper.selectByIds(wardIds)) {
                wards.put(w.getId(), w);
            }
        }
        return new Context(patients, doctors, beds, wards);
    }

    private AdmissionVO toVO(BizAdmission entity, Context context) {
        BizBed bed = entity.getBedId() == null ? null : context.beds().get(entity.getBedId());
        BizWard ward = bed == null ? null : context.wards().get(bed.getWardId());
        LocalDateTime end = entity.getDischargeTime() == null ? LocalDateTime.now() : entity.getDischargeTime();

        return AdmissionVO.builder()
                .id(String.valueOf(entity.getId()))
                .admissionNo(entity.getAdmissionNo())
                .patientId(entity.getPatientId())
                .patientName(context.patients().get(entity.getPatientId()))
                .deptId(entity.getDeptId())
                .bedId(entity.getBedId())
                .bedNo(bed == null ? null : bed.getBedNo())
                .wardId(bed == null ? null : bed.getWardId())
                .wardName(ward == null ? null : ward.getWardName())
                .doctorId(entity.getDoctorId())
                .doctorName(context.doctors().get(entity.getDoctorId()))
                .admissionTime(entity.getAdmissionTime())
                .dischargeTime(entity.getDischargeTime())
                .diagnosis(entity.getDiagnosis())
                .deposit(entity.getDeposit())
                .status(entity.getStatus())
                .stayDays(Math.max(1L, Duration.between(entity.getAdmissionTime(), end).toDays()))
                .build();
    }
}
