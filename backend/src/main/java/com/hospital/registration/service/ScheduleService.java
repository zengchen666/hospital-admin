package com.hospital.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.registration.dto.ScheduleQuery;
import com.hospital.registration.dto.ScheduleSaveRequest;
import com.hospital.registration.dto.ScheduleVO;
import com.hospital.registration.entity.BizSchedule;
import com.hospital.registration.mapper.BizScheduleMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排班与号源服务。
 */
@Service
public class ScheduleService {

    private final BizScheduleMapper scheduleMapper;
    private final SysUserMapper userMapper;

    public ScheduleService(BizScheduleMapper scheduleMapper, SysUserMapper userMapper) {
        this.scheduleMapper = scheduleMapper;
        this.userMapper = userMapper;
    }

    public PageResult<ScheduleVO> page(ScheduleQuery query) {
        LambdaQueryWrapper<BizSchedule> wrapper = Wrappers.<BizSchedule>lambdaQuery()
                .eq(query.getDoctorId() != null, BizSchedule::getDoctorId, query.getDoctorId())
                .eq(query.getDeptId() != null, BizSchedule::getDeptId, query.getDeptId())
                .eq(query.getTimePeriod() != null, BizSchedule::getTimePeriod, query.getTimePeriod())
                .eq(query.getRegType() != null, BizSchedule::getRegType, query.getRegType())
                .eq(query.getStatus() != null, BizSchedule::getStatus, query.getStatus());

        if (Boolean.TRUE.equals(query.getOnlyAvailable())) {
            // 挂号页只看能挂的：正常状态且有剩余号源
            wrapper.eq(BizSchedule::getStatus, 1).gt(BizSchedule::getRemainNum, 0);
        }
        if (query.getStartTime() != null) {
            wrapper.ge(BizSchedule::getScheduleDate, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(BizSchedule::getScheduleDate, query.getEndTime());
        }
        wrapper.orderByAsc(BizSchedule::getScheduleDate)
                .orderByAsc(BizSchedule::getTimePeriod)
                .orderByAsc(BizSchedule::getDoctorId);

        Page<BizSchedule> page = scheduleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> doctorNames = loadDoctorNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, doctorNames));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ScheduleSaveRequest request) {
        SysUser doctor = userMapper.selectById(request.getDoctorId());
        if (doctor == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "医生不存在");
        }

        BizSchedule entity = new BizSchedule();
        entity.setDoctorId(request.getDoctorId());
        entity.setDeptId(request.getDeptId());
        entity.setScheduleDate(request.getScheduleDate());
        entity.setTimePeriod(request.getTimePeriod());
        entity.setRegType(request.getRegType());
        entity.setTotalNum(request.getTotalNum());
        // 初始剩余号源 = 总号源；之后只能靠条件更新增减
        entity.setRemainNum(request.getTotalNum());
        entity.setRegFee(request.getRegFee());
        entity.setStatus(1);

        try {
            scheduleMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // uk_schedule(doctor_id, schedule_date, time_period, reg_type)
            throw new BusinessException(ErrorCode.DUPLICATE_DATA,
                    "该医生在 " + request.getScheduleDate() + " 的同一时段已有相同号别的排班");
        }
        return entity.getId();
    }

    /**
     * 停诊。
     *
     * <p>只置状态，<b>不删排班</b>：已经挂出去的号还要靠这条记录查医生与时段，
     * 删了历史挂号就成了孤儿数据。已挂号的患者由人工通知改约。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void stop(Long id) {
        BizSchedule existing = getExisting(id);
        if (Integer.valueOf(0).equals(existing.getStatus())) {
            return;
        }
        BizSchedule update = new BizSchedule();
        update.setId(id);
        update.setStatus(0);
        scheduleMapper.updateById(update);
    }

    public ScheduleVO detail(Long id) {
        BizSchedule entity = getExisting(id);
        return toVO(entity, loadDoctorNames(List.of(entity)));
    }

    private BizSchedule getExisting(Long id) {
        BizSchedule entity = scheduleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "排班不存在或已删除");
        }
        return entity;
    }

    /** 批量补医生姓名，避免前端逐行再查（N+1） */
    private Map<Long, String> loadDoctorNames(List<BizSchedule> schedules) {
        Set<Long> ids = schedules.stream().map(BizSchedule::getDoctorId).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (SysUser user : userMapper.selectByIds(ids)) {
            result.put(user.getId(), user.getRealName());
        }
        return result;
    }

    private ScheduleVO toVO(BizSchedule entity, Map<Long, String> doctorNames) {
        return ScheduleVO.builder()
                .id(String.valueOf(entity.getId()))
                .doctorId(entity.getDoctorId())
                .doctorName(doctorNames.get(entity.getDoctorId()))
                .deptId(entity.getDeptId())
                .scheduleDate(entity.getScheduleDate())
                .timePeriod(entity.getTimePeriod())
                .regType(entity.getRegType())
                .totalNum(entity.getTotalNum())
                .remainNum(entity.getRemainNum())
                .regFee(entity.getRegFee())
                .status(entity.getStatus())
                .build();
    }
}
