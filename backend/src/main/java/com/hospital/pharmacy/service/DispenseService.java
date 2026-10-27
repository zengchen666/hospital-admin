package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.clinic.entity.BizPrescription;
import com.hospital.clinic.entity.BizPrescriptionItem;
import com.hospital.clinic.mapper.BizPrescriptionItemMapper;
import com.hospital.clinic.mapper.BizPrescriptionMapper;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.pharmacy.dto.DispenseItemVO;
import com.hospital.pharmacy.dto.DispenseQuery;
import com.hospital.pharmacy.dto.PendingPrescVO;
import com.hospital.pharmacy.dto.DispenseRequest;
import com.hospital.pharmacy.dto.DispenseResult;
import com.hospital.pharmacy.dto.DispenseVO;
import com.hospital.pharmacy.entity.BizDispense;
import com.hospital.pharmacy.entity.BizDispenseItem;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.entity.BizStock;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.entity.BizWarehouse;
import com.hospital.pharmacy.mapper.BizDispenseItemMapper;
import com.hospital.pharmacy.mapper.BizDispenseMapper;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
import com.hospital.pharmacy.mapper.BizWarehouseMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门诊发药服务。
 *
 * <p><b>按批号扣减、近效期优先</b>：处方只说「某药 N 盒」，但库存是按批号分开存的。
 * 发药时必须把 N 盒拆到具体批次上，否则无法扣减、也无法在药品召回时定位到人。</p>
 *
 * <p><b>三条不变量</b>：</p>
 * <ol>
 *   <li>库存不为负 —— 扣减走 {@code WHERE quantity >= n} 条件更新；</li>
 *   <li>发药数量守恒 —— 每个药品各批次出库量之和 == 处方数量，不足则整单回滚；</li>
 *   <li>库存与流水同事务 —— 扣了库存必留流水，绝不出现「账实不符还查不到原因」。</li>
 * </ol>
 */
@Slf4j
@Service
public class DispenseService {

    private static final int PRESC_PENDING_AUDIT = 0;
    private static final int PRESC_AUDITED = 1;
    private static final int PRESC_CHARGED = 2;
    private static final int PRESC_DISPENSED = 3;
    private static final int PRESC_CANCELLED = 4;

    private static final int DISPENSE_WAITING = 0;
    private static final int DISPENSE_DONE = 1;
    private static final int DISPENSE_TYPE_OUTPATIENT = 1;
    private static final int RECORD_TYPE_OUT = 2;

    private final BizDispenseMapper dispenseMapper;
    private final BizDispenseItemMapper dispenseItemMapper;
    private final BizStockMapper stockMapper;
    private final BizStockRecordMapper stockRecordMapper;
    private final BizWarehouseMapper warehouseMapper;
    private final BizPrescriptionMapper prescriptionMapper;
    private final BizPrescriptionItemMapper prescriptionItemMapper;
    private final BizDrugMapper drugMapper;
    private final BizPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final DispenseNoGenerator noGenerator;

    public DispenseService(BizDispenseMapper dispenseMapper,
                           BizDispenseItemMapper dispenseItemMapper,
                           BizStockMapper stockMapper,
                           BizStockRecordMapper stockRecordMapper,
                           BizWarehouseMapper warehouseMapper,
                           BizPrescriptionMapper prescriptionMapper,
                           BizPrescriptionItemMapper prescriptionItemMapper,
                           BizDrugMapper drugMapper,
                           BizPatientMapper patientMapper,
                           SysUserMapper userMapper,
                           DispenseNoGenerator noGenerator) {
        this.dispenseMapper = dispenseMapper;
        this.dispenseItemMapper = dispenseItemMapper;
        this.stockMapper = stockMapper;
        this.stockRecordMapper = stockRecordMapper;
        this.warehouseMapper = warehouseMapper;
        this.prescriptionMapper = prescriptionMapper;
        this.prescriptionItemMapper = prescriptionItemMapper;
        this.drugMapper = drugMapper;
        this.patientMapper = patientMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public DispenseResult dispense(DispenseRequest request) {
        BizPrescription prescription = prescriptionMapper.selectById(request.getPrescId());
        if (prescription == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "处方不存在");
        }
        if (Integer.valueOf(PRESC_CANCELLED).equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方已作废，不能发药");
        }
        if (Integer.valueOf(PRESC_PENDING_AUDIT).equals(prescription.getStatus())
                || Integer.valueOf(PRESC_AUDITED).equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方尚未收费，不能发药");
        }
        if (Integer.valueOf(PRESC_DISPENSED).equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该处方已发药，请勿重复操作");
        }

        BizWarehouse warehouse = warehouseMapper.selectById(request.getWarehouseId());
        if (warehouse == null || !Integer.valueOf(1).equals(warehouse.getStatus())) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "发药库房不存在或已停用");
        }

        List<BizPrescriptionItem> items = prescriptionItemMapper.selectList(
                Wrappers.<BizPrescriptionItem>lambdaQuery()
                        .eq(BizPrescriptionItem::getPrescId, prescription.getId()));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "处方没有药品明细，无法发药");
        }

        Long operatorId = CurrentUser.requireId();
        BizDispense dispense = new BizDispense();
        dispense.setDispenseNo(noGenerator.next());
        dispense.setPrescId(prescription.getId());
        dispense.setPatientId(prescription.getPatientId());
        dispense.setWarehouseId(warehouse.getId());
        dispense.setDispenseType(DISPENSE_TYPE_OUTPATIENT);
        dispense.setTotalAmount(BigDecimal.ZERO);
        dispense.setStatus(DISPENSE_WAITING);
        dispenseMapper.insert(dispense);

        BigDecimal totalAmount = BigDecimal.ZERO;
        int itemCount = 0;

        for (BizPrescriptionItem item : items) {
            int need = item.getQuantity() == null ? 0 : item.getQuantity();
            if (need <= 0) {
                continue;
            }
            List<BizStock> batches = stockMapper.selectAvailable(item.getDrugId(), warehouse.getId());
            if (batches.isEmpty()) {
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                        "「" + item.getDrugName() + "」在该库房已无库存");
            }

            for (BizStock batch : batches) {
                if (need <= 0) {
                    break;
                }
                int take = Math.min(need, batch.getQuantity());
                // 条件更新：余量真的够才会扣成功；被并发抢走就试下一批
                if (stockMapper.decrease(batch.getId(), take) == 0) {
                    continue;
                }

                BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
                BigDecimal amount = price.multiply(BigDecimal.valueOf(take));

                BizDispenseItem dispenseItem = new BizDispenseItem();
                dispenseItem.setDispenseId(dispense.getId());
                dispenseItem.setDrugId(item.getDrugId());
                dispenseItem.setBatchNo(batch.getBatchNo());
                dispenseItem.setQuantity(take);
                dispenseItem.setPrice(price);
                dispenseItem.setAmount(amount);
                dispenseItemMapper.insert(dispenseItem);

                // 库存变动必留流水，与扣减同事务
                BizStockRecord record = new BizStockRecord();
                record.setDrugId(item.getDrugId());
                record.setWarehouseId(warehouse.getId());
                record.setRecordType(RECORD_TYPE_OUT);
                record.setQuantity(take);
                record.setBatchNo(batch.getBatchNo());
                record.setRelatedNo(dispense.getDispenseNo());
                record.setOperatorId(operatorId);
                record.setRemark("门诊发药 " + prescription.getPrescNo());
                stockRecordMapper.insert(record);

                totalAmount = totalAmount.add(amount);
                need -= take;
                itemCount++;
            }

            if (need > 0) {
                // 抛异常 → 整个事务回滚，已扣的库存与已写的流水一并撤销
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                        "「" + item.getDrugName() + "」库存不足，还差 " + need);
            }
        }

        BizDispense update = new BizDispense();
        update.setId(dispense.getId());
        update.setTotalAmount(totalAmount);
        dispenseMapper.updateById(update);

        if (dispenseMapper.transitStatus(dispense.getId(), DISPENSE_WAITING, DISPENSE_DONE, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "发药单状态已变化，请刷新后重试");
        }
        // 处方 2 → 3
        if (prescriptionMapper.transitStatus(prescription.getId(), PRESC_CHARGED, PRESC_DISPENSED, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "处方状态已变化，请刷新后重试");
        }

        log.info("发药成功 dispenseNo={} prescNo={} batchItems={} amount={}",
                dispense.getDispenseNo(), prescription.getPrescNo(), itemCount, totalAmount);

        return DispenseResult.builder()
                .id(String.valueOf(dispense.getId()))
                .dispenseNo(dispense.getDispenseNo())
                .itemCount(itemCount)
                .totalAmount(totalAmount)
                .status(DISPENSE_DONE)
                .build();
    }

    public PageResult<DispenseVO> page(DispenseQuery query) {
        LambdaQueryWrapper<BizDispense> wrapper = Wrappers.<BizDispense>lambdaQuery()
                .eq(query.getPrescId() != null, BizDispense::getPrescId, query.getPrescId())
                .eq(query.getPatientId() != null, BizDispense::getPatientId, query.getPatientId())
                .eq(query.getWarehouseId() != null, BizDispense::getWarehouseId, query.getWarehouseId())
                .eq(query.getStatus() != null, BizDispense::getStatus, query.getStatus())
                .eq(query.getDispenseType() != null, BizDispense::getDispenseType, query.getDispenseType());

        if (query.getStartTime() != null) {
            wrapper.ge(BizDispense::getDispenseTime, query.getStartTime().atStartOfDay());
        }
        if (query.getEndTime() != null) {
            wrapper.lt(BizDispense::getDispenseTime, query.getEndTime().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(BizDispense::getDispenseTime);

        Page<BizDispense> page = dispenseMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        Map<Long, List<DispenseItemVO>> itemMap = loadItems(page.getRecords());
        Names names = loadNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, itemMap, names));
    }

    public DispenseVO detail(Long id) {
        BizDispense entity = dispenseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "发药单不存在或已删除");
        }
        return toVO(entity, loadItems(List.of(entity)), loadNames(List.of(entity)));
    }

    /**
     * 待发药处方列表（status = 2 已收费）。
     *
     * <p><b>只列已收费的处方</b>：已审核未收费的处方发出去，钱没收、药先走了，
     * 后面补收会变成一笔说不清的账。收费是发药的前置条件，不是可选项。</p>
     *
     * <p>带上可用库存是为了让窗口在发药前就知道「这个药够不够」 ——
     * 发到一半发现库存不足要整单回滚，比一开始就提示要糟得多。</p>
     */
    public PageResult<PendingPrescVO> pending(String prescNo, String patientKeyword,
                                              long pageNum, long pageSize) {
        LambdaQueryWrapper<BizPrescription> wrapper = Wrappers.<BizPrescription>lambdaQuery()
                .eq(BizPrescription::getStatus, PRESC_CHARGED);

        if (StringUtils.hasText(prescNo)) {
            wrapper.like(BizPrescription::getPrescNo, prescNo.trim());
        }
        if (StringUtils.hasText(patientKeyword)) {
            List<Long> patientIds = patientMapper.selectList(Wrappers.<BizPatient>lambdaQuery()
                            .like(BizPatient::getName, patientKeyword.trim()))
                    .stream().map(BizPatient::getId).toList();
            if (patientIds.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
            wrapper.in(BizPrescription::getPatientId, patientIds);
        }
        wrapper.orderByAsc(BizPrescription::getCreateTime);

        Page<BizPrescription> page = prescriptionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizPrescription> rows = page.getRecords();
        if (rows.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        Set<Long> ids = rows.stream().map(BizPrescription::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<BizPrescriptionItem>> itemMap = new HashMap<>();
        for (BizPrescriptionItem item : prescriptionItemMapper.selectList(
                Wrappers.<BizPrescriptionItem>lambdaQuery().in(BizPrescriptionItem::getPrescId, ids))) {
            itemMap.computeIfAbsent(item.getPrescId(), k -> new ArrayList<>()).add(item);
        }

        Set<Long> drugIds = itemMap.values().stream().flatMap(List::stream)
                .map(BizPrescriptionItem::getDrugId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizDrug> drugs = new HashMap<>();
        if (!drugIds.isEmpty()) {
            for (BizDrug d : drugMapper.selectByIds(drugIds)) {
                drugs.put(d.getId(), d);
            }
        }
        Map<Long, Integer> stock = new HashMap<>();
        if (!drugIds.isEmpty()) {
            stockMapper.selectList(Wrappers.<BizStock>lambdaQuery().in(BizStock::getDrugId, drugIds))
                    .forEach(s -> stock.merge(s.getDrugId(),
                            s.getQuantity() == null ? 0 : s.getQuantity(), Integer::sum));
        }

        Names names = loadNamesForPresc(rows, itemMap);
        return PageResult.of(page, p -> toPendingVO(p, itemMap, drugs, stock, names));
    }

    /** 发药库房下拉：只返回启用中的库房，按编码排序 */
    public List<BizWarehouse> warehouseOptions() {
        return warehouseMapper.selectList(Wrappers.<BizWarehouse>lambdaQuery()
                .eq(BizWarehouse::getStatus, 1)
                .orderByAsc(BizWarehouse::getWarehouseCode));
    }

    private Map<Long, List<DispenseItemVO>> loadItems(List<BizDispense> dispenses) {
        if (dispenses.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = dispenses.stream().map(BizDispense::getId).collect(Collectors.toSet());
        List<BizDispenseItem> items = dispenseItemMapper.selectList(
                Wrappers.<BizDispenseItem>lambdaQuery().in(BizDispenseItem::getDispenseId, ids));
        if (items.isEmpty()) {
            return Map.of();
        }

        Set<Long> drugIds = items.stream().map(BizDispenseItem::getDrugId).collect(Collectors.toSet());
        Map<Long, String> drugNames = new HashMap<>();
        for (BizDrug drug : drugMapper.selectByIds(drugIds)) {
            drugNames.put(drug.getId(), drug.getDrugName());
        }

        Map<Long, List<DispenseItemVO>> result = new HashMap<>();
        for (BizDispenseItem item : items) {
            result.computeIfAbsent(item.getDispenseId(), k -> new ArrayList<>())
                    .add(DispenseItemVO.builder()
                            .id(String.valueOf(item.getId()))
                            .drugId(item.getDrugId())
                            .drugName(drugNames.get(item.getDrugId()))
                            .batchNo(item.getBatchNo())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .amount(item.getAmount())
                            .build());
        }
        return result;
    }

    private record Names(Map<Long, String> patients, Map<Long, String> warehouses, Map<Long, String> operators) {
    }

    private Names loadNames(List<BizDispense> dispenses) {
        Set<Long> patientIds = dispenses.stream().map(BizDispense::getPatientId).collect(Collectors.toSet());
        Set<Long> warehouseIds = dispenses.stream().map(BizDispense::getWarehouseId).collect(Collectors.toSet());
        Set<Long> operatorIds = dispenses.stream()
                .map(BizDispense::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (BizPatient p : patientMapper.selectByIds(patientIds)) {
                patients.put(p.getId(), p.getName());
            }
        }
        Map<Long, String> warehouses = new HashMap<>();
        if (!warehouseIds.isEmpty()) {
            for (BizWarehouse w : warehouseMapper.selectByIds(warehouseIds)) {
                warehouses.put(w.getId(), w.getWarehouseName());
            }
        }
        Map<Long, String> operators = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(operatorIds)) {
                operators.put(u.getId(), u.getRealName());
            }
        }
        return new Names(patients, warehouses, operators);
    }

    /** 待发药列表用：患者 + 开方医生姓名 */
    private Names loadNamesForPresc(List<BizPrescription> prescs,
                                    Map<Long, List<BizPrescriptionItem>> itemMap) {
        Set<Long> patientIds = prescs.stream().map(BizPrescription::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> doctorIds = prescs.stream().map(BizPrescription::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

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
        return new Names(patients, Map.of(), doctors);
    }

    private PendingPrescVO toPendingVO(BizPrescription p,
                                       Map<Long, List<BizPrescriptionItem>> itemMap,
                                       Map<Long, BizDrug> drugs,
                                       Map<Long, Integer> stock,
                                       Names names) {
        List<PendingPrescVO.PendingItemVO> items = itemMap.getOrDefault(p.getId(), List.of()).stream()
                .map(i -> {
                    BizDrug drug = drugs.get(i.getDrugId());
                    return PendingPrescVO.PendingItemVO.builder()
                            .drugId(String.valueOf(i.getDrugId()))
                            .drugName(drug == null ? null : drug.getDrugName())
                            .spec(drug == null ? null : drug.getSpec())
                            .unit(drug == null ? null : drug.getUnit())
                            .quantity(i.getQuantity())
                            .price(i.getPrice())
                            .amount(i.getAmount())
                            .availableStock(stock.get(i.getDrugId()))
                            .build();
                }).toList();

        return PendingPrescVO.builder()
                .prescId(String.valueOf(p.getId()))
                .prescNo(p.getPrescNo())
                .patientId(String.valueOf(p.getPatientId()))
                .patientName(names.patients().get(p.getPatientId()))
                .doctorId(p.getDoctorId() == null ? null : String.valueOf(p.getDoctorId()))
                .doctorName(names.operators().get(p.getDoctorId()))
                .diagnosis(p.getDiagnosis())
                .totalAmount(p.getTotalAmount())
                .prescType(p.getPrescType())
                .status(p.getStatus())
                .createTime(p.getCreateTime())
                .items(items)
                .build();
    }

    private DispenseVO toVO(BizDispense entity, Map<Long, List<DispenseItemVO>> itemMap, Names names) {
        return DispenseVO.builder()
                .id(String.valueOf(entity.getId()))
                .dispenseNo(entity.getDispenseNo())
                .prescId(entity.getPrescId())
                .patientId(entity.getPatientId())
                .patientName(names.patients().get(entity.getPatientId()))
                .warehouseId(entity.getWarehouseId())
                .warehouseName(names.warehouses().get(entity.getWarehouseId()))
                .dispenseType(entity.getDispenseType())
                .totalAmount(entity.getTotalAmount())
                .operatorId(entity.getOperatorId())
                .operatorName(names.operators().get(entity.getOperatorId()))
                .dispenseTime(entity.getDispenseTime())
                .status(entity.getStatus())
                .items(itemMap.getOrDefault(entity.getId(), List.of()))
                .build();
    }
}
