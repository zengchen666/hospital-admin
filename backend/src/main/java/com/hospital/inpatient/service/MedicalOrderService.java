package com.hospital.inpatient.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.inpatient.dto.OrderRequest;
import com.hospital.inpatient.dto.OrderVO;
import com.hospital.inpatient.entity.BizAdmission;
import com.hospital.inpatient.entity.BizMedicalOrder;
import com.hospital.inpatient.mapper.BizAdmissionMapper;
import com.hospital.inpatient.mapper.BizMedicalOrderMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 住院医嘱服务：开立、执行、停止、查询。
 *
 * <p>只有「在院」的患者能开医嘱；已出院的患者不能再新增医嘱，
 * 否则出院结算后还会冒出费用。</p>
 */
@Slf4j
@Service
public class MedicalOrderService {

    private static final int ADMISSION_IN_HOSPITAL = 0;
    private static final int ORDER_STOPPED = 2;

    private final BizMedicalOrderMapper orderMapper;
    private final BizAdmissionMapper admissionMapper;
    private final SysUserMapper userMapper;

    public MedicalOrderService(BizMedicalOrderMapper orderMapper,
                               BizAdmissionMapper admissionMapper,
                               SysUserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.admissionMapper = admissionMapper;
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(OrderRequest request) {
        BizAdmission admission = admissionMapper.selectById(request.getAdmissionId());
        if (admission == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "住院记录不存在");
        }
        if (!Integer.valueOf(ADMISSION_IN_HOSPITAL).equals(admission.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "患者已出院，不能再开立医嘱");
        }

        BizMedicalOrder order = new BizMedicalOrder();
        order.setAdmissionId(request.getAdmissionId());
        order.setOrderType(request.getOrderType());
        order.setContent(request.getContent());
        order.setDrugId(request.getDrugId());
        order.setDosage(request.getDosage());
        order.setFrequency(request.getFrequency());
        order.setStartTime(request.getStartTime() == null ? LocalDateTime.now() : request.getStartTime());
        // 开嘱医生取当前登录用户，不接受前端传值
        order.setDoctorId(CurrentUser.requireId());
        order.setStatus(0);
        orderMapper.insert(order);

        log.info("开立医嘱成功 admissionId={} type={} content={}",
                request.getAdmissionId(), request.getOrderType(), request.getContent());
        return order.getId();
    }

    /** 执行医嘱：未执行 → 已执行（临时医嘱一般执行一次即完成） */
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long id) {
        getExisting(id);
        if (orderMapper.execute(id, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "只有未执行的医嘱可以执行");
        }
    }

    /**
     * 停止医嘱。
     *
     * <p>长期医嘱停止后不可恢复，需要重新开立 —— 医嘱是法律文书，
     * 不能像开关一样来回切。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void stop(Long id) {
        BizMedicalOrder order = getExisting(id);
        if (Integer.valueOf(ORDER_STOPPED).equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该医嘱已停止");
        }
        if (orderMapper.stop(id, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "医嘱状态已变化，请刷新后重试");
        }
    }

    public List<OrderVO> listByAdmission(Long admissionId) {
        List<BizMedicalOrder> orders = orderMapper.selectList(Wrappers.<BizMedicalOrder>lambdaQuery()
                .eq(BizMedicalOrder::getAdmissionId, admissionId)
                .orderByDesc(BizMedicalOrder::getCreateTime));
        if (orders.isEmpty()) {
            return List.of();
        }

        Set<Long> doctorIds = orders.stream().map(BizMedicalOrder::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        java.util.Map<Long, String> doctors = new HashMap<>();
        if (!doctorIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(doctorIds)) {
                doctors.put(u.getId(), u.getRealName());
            }
        }

        return orders.stream().map(order -> OrderVO.builder()
                .id(String.valueOf(order.getId()))
                .admissionId(String.valueOf(order.getAdmissionId()))
                .orderType(order.getOrderType())
                .content(order.getContent())
                .drugId(order.getDrugId())
                .dosage(order.getDosage())
                .frequency(order.getFrequency())
                .startTime(order.getStartTime())
                .stopTime(order.getStopTime())
                .doctorId(order.getDoctorId())
                .doctorName(doctors.get(order.getDoctorId()))
                .status(order.getStatus())
                .createTime(order.getCreateTime())
                .build()).toList();
    }

    private BizMedicalOrder getExisting(Long id) {
        BizMedicalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "医嘱不存在");
        }
        return order;
    }
}
