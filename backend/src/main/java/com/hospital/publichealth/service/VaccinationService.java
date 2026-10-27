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
import com.hospital.publichealth.dto.VaccinationQuery;
import com.hospital.publichealth.dto.VaccinationRequest;
import com.hospital.publichealth.dto.VaccinationVO;
import com.hospital.publichealth.entity.BizVaccination;
import com.hospital.publichealth.mapper.BizVaccinationMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 疫苗接种服务。
 *
 * <p>三条硬规则（都写在方法注释里，别在重构时顺手删掉）：</p>
 * <ol>
 *   <li><b>批号必须真实存在</b>：疫苗出问题时靠批号追溯「哪些人打了哪一批」，
 *       手写批号等于断了追溯链；</li>
 *   <li><b>剂次不能重复</b>：同人同苗同剂次只允许一条，否则提醒列表会重复催种，
 *       还可能被当成漏种补打一针；</li>
 *   <li><b>接种日期不能是未来</b>：接种是已发生的事实。</li>
 * </ol>
 *
 * <p><b>一期只登记，不扣库存</b>：库存扣减属于药房出库职责，
 * 让接种动作直接改库存会让「谁动了我的库存」变得说不清。
 * 这里只校验批号有效，实际出库走药房流程。</p>
 */
@Slf4j
@Service
public class VaccinationService {

    /** 接种提醒的默认前瞻天数 */
    public static final int DEFAULT_REMIND_DAYS = 30;
    /** 提醒列表硬上限，防止把全库接种记录拉回来 */
    public static final int REMIND_MAX_ROWS = 500;

    private final BizVaccinationMapper vaccinationMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;

    public VaccinationService(BizVaccinationMapper vaccinationMapper,
                              BizPatientMapper patientMapper,
                              SysUserMapper userMapper) {
        this.vaccinationMapper = vaccinationMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(VaccinationRequest request) {
        // 接种人必须落到具体的人身上，先把登录态验掉再走业务校验
        Long operatorId = CurrentUser.requireId();

        BizPatient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "患者不存在，请先建档");
        }
        if (request.getVaccinateDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "接种日期不能晚于今天");
        }
        if (StringUtils.hasText(request.getBatchNo())
                && vaccinationMapper.countVaccineBatch(request.getBatchNo()) == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "疫苗批号 " + request.getBatchNo() + " 不存在，请核对后重新录入");
        }

        Integer doseNo = request.getDoseNo();
        if (doseNo == null) {
            doseNo = vaccinationMapper.selectMaxDoseNo(patient.getId(), request.getVaccineName()) + 1;
        } else if (doseNo <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "剂次必须大于 0");
        }
        if (vaccinationMapper.countSameDose(patient.getId(), request.getVaccineName(), doseNo) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA,
                    "该居民已接种「" + request.getVaccineName() + "」第 " + doseNo + " 剂，请勿重复登记");
        }

        BizVaccination entity = new BizVaccination();
        entity.setPatientId(patient.getId());
        entity.setVaccineName(request.getVaccineName());
        entity.setBatchNo(request.getBatchNo());
        entity.setDoseNo(doseNo);
        entity.setVaccinateDate(request.getVaccinateDate());
        entity.setOperatorId(operatorId);
        entity.setNextDate(request.getNextDate());
        entity.setCreateBy(operatorId);
        vaccinationMapper.insert(entity);

        log.info("疫苗接种登记成功 patientId={} vaccine={} dose={} batch={}",
                patient.getId(), entity.getVaccineName(), doseNo, entity.getBatchNo());
        return entity.getId();
    }

    public PageResult<VaccinationVO> page(VaccinationQuery query) {
        LambdaQueryWrapper<BizVaccination> wrapper = Wrappers.<BizVaccination>lambdaQuery()
                .eq(query.getPatientId() != null, BizVaccination::getPatientId, query.getPatientId())
                .eq(StringUtils.hasText(query.getBatchNo()), BizVaccination::getBatchNo, query.getBatchNo());

        if (StringUtils.hasText(query.getVaccineName())) {
            wrapper.like(BizVaccination::getVaccineName, query.getVaccineName());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            List<Long> matched = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .like(BizPatient::getName, kw))
                    .stream().map(BizPatient::getId).toList();
            wrapper.and(w -> w.like(BizVaccination::getVaccineName, kw)
                    .or(!matched.isEmpty(), q -> q.in(BizVaccination::getPatientId, matched)));
        }
        wrapper.orderByDesc(BizVaccination::getVaccinateDate).orderByDesc(BizVaccination::getId);

        Page<BizVaccination> page = vaccinationMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        // 姓名先一次查完再逐条拼装 —— 放进 lambda 里会变成每条记录查一次全表
        Map<Long, String> names = loadPatientNames(idsOf(page.getRecords(), BizVaccination::getPatientId));
        Map<Long, String> userNames = loadUserNames(idsOf(page.getRecords(), BizVaccination::getOperatorId));
        return PageResult.of(page, entity -> toVO(entity, names, userNames));
    }

    /**
     * 接种提醒：未来 {@code days} 天内到期或已逾期的接种。
     *
     * <p><b>不设下限</b>：逾期未种的比即将到期的更该催，所以一并纳入。</p>
     */
    public List<VaccinationVO> remind(Integer days) {
        int lookAhead = (days == null || days <= 0) ? DEFAULT_REMIND_DAYS : Math.min(days, 365);
        List<BizVaccination> list = vaccinationMapper.selectRemind(lookAhead, REMIND_MAX_ROWS);
        Map<Long, String> names = loadPatientNames(idsOf(list, BizVaccination::getPatientId));
        Map<Long, String> userNames = loadUserNames(idsOf(list, BizVaccination::getOperatorId));
        return list.stream().map(entity -> toVO(entity, names, userNames)).toList();
    }

    private static Set<Long> idsOf(List<BizVaccination> list,
                                   java.util.function.Function<BizVaccination, Long> getter) {
        return list.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 转 VO：患者姓名与接种人姓名由外部一次性查好传进来，避免逐条 selectById 的 N+1 */
    private VaccinationVO toVO(BizVaccination entity, Map<Long, String> names, Map<Long, String> userNames) {
        LocalDate next = entity.getNextDate();
        return VaccinationVO.builder()
                .id(String.valueOf(entity.getId()))
                .patientId(entity.getPatientId())
                .patientName(names.get(entity.getPatientId()))
                .vaccineName(entity.getVaccineName())
                .batchNo(entity.getBatchNo())
                .doseNo(entity.getDoseNo())
                .vaccinateDate(entity.getVaccinateDate())
                .operatorId(entity.getOperatorId())
                .operatorName(userNames.get(entity.getOperatorId()))
                .nextDate(next)
                .overdue(next != null && next.isBefore(LocalDate.now()))
                .build();
    }

    /** 供 {@link #page} 使用：一次性补齐姓名，替代逐条 selectById */
    private Map<Long, String> loadPatientNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (BizPatient p : patientMapper.selectByIds(ids)) {
            map.put(p.getId(), p.getName());
        }
        return map;
    }

    private Map<Long, String> loadUserNames(Set<Long> ids) {
        Set<Long> valid = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysUser u : userMapper.selectByIds(valid)) {
            map.put(u.getId(), u.getRealName());
        }
        return map;
    }
}
