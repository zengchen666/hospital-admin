package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.pharmacy.dto.PurchaseInRequest;
import com.hospital.pharmacy.dto.PurchaseItemRequest;
import com.hospital.pharmacy.dto.PurchaseItemVO;
import com.hospital.pharmacy.dto.PurchaseQuery;
import com.hospital.pharmacy.dto.PurchaseRequest;
import com.hospital.pharmacy.dto.PurchaseVO;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.entity.BizPurchase;
import com.hospital.pharmacy.entity.BizPurchaseItem;
import com.hospital.pharmacy.entity.BizStock;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.entity.BizSupplier;
import com.hospital.pharmacy.entity.BizWarehouse;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.pharmacy.mapper.BizPurchaseItemMapper;
import com.hospital.pharmacy.mapper.BizPurchaseMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
import com.hospital.pharmacy.mapper.BizSupplierMapper;
import com.hospital.pharmacy.mapper.BizWarehouseMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购管理：建单 → 审核 → 入库。
 *
 * <p><b>审核不等于入库</b>，这是本模块最重要的设计决定。
 * 审核只是「同意买」，货有没有到、到了多少、效期如何，是验收时才知道的事。
 * 审核即加库存会让库存凭空多出一批尚未验收的药，
 * 而实物还在供应商车上 —— 账实不符从这里就开始了。</p>
 *
 * <p><b>不相容职务</b>：审核人不能是自己提交的单据（见 {@link #audit}）。
 * 和处方「开方医生不能审核自己的处方」是同一条内控原则。</p>
 */
@Slf4j
@Service
public class PurchaseService {

    /** 出入库流水：1 入库 */
    private static final int RECORD_IN = 1;

    private final BizPurchaseMapper purchaseMapper;
    private final BizPurchaseItemMapper itemMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizWarehouseMapper warehouseMapper;
    private final BizDrugMapper drugMapper;
    private final BizStockMapper stockMapper;
    private final BizStockRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final PurchaseNoGenerator noGenerator;

    public PurchaseService(BizPurchaseMapper purchaseMapper,
                           BizPurchaseItemMapper itemMapper,
                           BizSupplierMapper supplierMapper,
                           BizWarehouseMapper warehouseMapper,
                           BizDrugMapper drugMapper,
                           BizStockMapper stockMapper,
                           BizStockRecordMapper recordMapper,
                           SysUserMapper userMapper,
                           PurchaseNoGenerator noGenerator) {
        this.purchaseMapper = purchaseMapper;
        this.itemMapper = itemMapper;
        this.supplierMapper = supplierMapper;
        this.warehouseMapper = warehouseMapper;
        this.drugMapper = drugMapper;
        this.stockMapper = stockMapper;
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 新建采购单，落「待审核」。
     *
     * <p>金额一律服务端算：{@code quantity × price} 逐条累加。
     * 让前端传总金额，等于把篡改成本降到改一个 JSON 字段。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseRequest request) {
        BizSupplier supplier = supplierMapper.selectById(request.getSupplierId());
        if (supplier == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "供应商不存在");
        }
        if (Integer.valueOf(0).equals(supplier.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "该供应商已停用，不能新建采购单");
        }
        BizWarehouse warehouse = warehouseMapper.selectById(request.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "库房不存在");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItemRequest item : request.getItems()) {
            BizDrug drug = drugMapper.selectById(item.getDrugId());
            if (drug == null) {
                throw BusinessException.of(ErrorCode.NO_RESOURCE, "药品 %s 不存在", item.getDrugId());
            }
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BizPurchase purchase = new BizPurchase();
        purchase.setPurchaseNo(noGenerator.next());
        purchase.setSupplierId(request.getSupplierId());
        purchase.setWarehouseId(request.getWarehouseId());
        purchase.setTotalAmount(total);
        purchase.setExpectDate(request.getExpectDate());
        purchase.setStatus(BizPurchase.STATUS_PENDING);
        purchase.setApplicantId(CurrentUser.requireId());
        purchase.setRemark(request.getRemark());
        purchaseMapper.insert(purchase);

        for (PurchaseItemRequest item : request.getItems()) {
            BizPurchaseItem entity = new BizPurchaseItem();
            entity.setPurchaseId(purchase.getId());
            entity.setDrugId(item.getDrugId());
            entity.setQuantity(item.getQuantity());
            entity.setPrice(item.getPrice());
            entity.setAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            entity.setArriveNum(0);
            itemMapper.insert(entity);
        }

        log.info("新建采购单 no={} supplier={} amount={}",
                purchase.getPurchaseNo(), supplier.getSupplierName(), total);
        return purchase.getId();
    }

    /**
     * 采购单审核。
     *
     * <p><b>申请人不能审自己的单</b>：和「开方医生不能审核自己的处方」同源于不相容职务分离。
     * 自己提自己批，采购这条线上的金额管控就形同虚设。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        BizPurchase purchase = getExisting(id);
        if (!Integer.valueOf(BizPurchase.STATUS_PENDING).equals(purchase.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "只有待审核的采购单可以审核");
        }
        Long auditorId = CurrentUser.requireId();
        if (auditorId.equals(purchase.getApplicantId())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "申请人不能审核自己提交的采购单");
        }
        if (purchaseMapper.audit(id, auditorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "采购单状态已变化，请刷新后重试");
        }
        log.info("采购单审核通过 no={} auditor={}", purchase.getPurchaseNo(), auditorId);
    }

    /**
     * 采购入库：加库存 + 写流水 + 回填实到数量 + 单据置「已入库」。
     *
     * <p>四件事同一事务，少任何一件账都不平：
     * 只加库存不写流水 = 查不到这批货哪来的；
     * 只写流水不回填实到 = 采购单永远显示「未到货」。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long id, PurchaseInRequest request) {
        BizPurchase purchase = getExisting(id);
        if (!Integer.valueOf(BizPurchase.STATUS_AUDITED).equals(purchase.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "只有已审核的采购单可以入库");
        }

        List<BizPurchaseItem> items = itemMapper.selectList(Wrappers.<BizPurchaseItem>lambdaQuery()
                .eq(BizPurchaseItem::getPurchaseId, id));
        Map<Long, BizPurchaseItem> itemMap = new HashMap<>();
        items.forEach(i -> itemMap.put(i.getId(), i));

        Long operatorId = CurrentUser.idOrNull();
        for (PurchaseInRequest.PurchaseInItemRequest in : request.getItems()) {
            BizPurchaseItem item = itemMap.get(in.getItemId());
            if (item == null) {
                throw BusinessException.of(ErrorCode.NO_RESOURCE, "采购明细 %s 不存在", in.getItemId());
            }
            if (in.getArriveNum() == null || in.getArriveNum() <= 0) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "实到数量必须大于 0；未到货的明细请从入库单中移除", in.getItemId());
            }
            if (in.getArriveNum() > item.getQuantity()) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "实到数量 %d 超过采购数量 %d", in.getArriveNum(), item.getQuantity());
            }
            if (in.getExpireDate() == null || !in.getExpireDate().isAfter(LocalDate.now())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "有效期必须晚于今天");
            }

            BizStock existing = stockMapper.selectOne(Wrappers.<BizStock>lambdaQuery()
                    .eq(BizStock::getDrugId, item.getDrugId())
                    .eq(BizStock::getWarehouseId, purchase.getWarehouseId())
                    .eq(BizStock::getBatchNo, in.getBatchNo()));
            if (existing == null) {
                BizStock stock = new BizStock();
                stock.setDrugId(item.getDrugId());
                stock.setWarehouseId(purchase.getWarehouseId());
                stock.setBatchNo(in.getBatchNo());
                stock.setQuantity(in.getArriveNum());
                stock.setWarnNum(request.getWarnNum() == null ? 0 : request.getWarnNum());
                stock.setProduceDate(request.getProduceDate());
                stock.setExpireDate(in.getExpireDate());
                stockMapper.insert(stock);
            } else {
                BizStock update = new BizStock();
                update.setId(existing.getId());
                update.setQuantity(existing.getQuantity() + in.getArriveNum());
                update.setExpireDate(in.getExpireDate());
                if (request.getProduceDate() != null) {
                    update.setProduceDate(request.getProduceDate());
                }
                stockMapper.updateById(update);
            }

            BizStockRecord record = new BizStockRecord();
            record.setDrugId(item.getDrugId());
            record.setWarehouseId(purchase.getWarehouseId());
            record.setRecordType(RECORD_IN);
            record.setQuantity(in.getArriveNum());
            record.setBatchNo(in.getBatchNo());
            record.setRelatedNo(purchase.getPurchaseNo());
            record.setOperatorId(operatorId);
            record.setRemark(request.getRemark());
            recordMapper.insert(record);

            BizPurchaseItem itemUpdate = new BizPurchaseItem();
            itemUpdate.setId(item.getId());
            itemUpdate.setArriveNum(in.getArriveNum());
            itemMapper.updateById(itemUpdate);
        }

        if (purchaseMapper.markStocked(id, operatorId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "采购单状态已变化，请刷新后重试");
        }
        log.info("采购入库完成 no={} 明细数={}", purchase.getPurchaseNo(), request.getItems().size());
    }

    /** 作废：已入库的单子不能作废，货已经在库里了 */
    @Transactional(rollbackFor = Exception.class)
    public void voidPurchase(Long id) {
        BizPurchase purchase = getExisting(id);
        if (Integer.valueOf(BizPurchase.STATUS_STOCKED).equals(purchase.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已入库的采购单不能作废");
        }
        if (purchaseMapper.voidPurchase(id, CurrentUser.idOrNull()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "采购单状态已变化，请刷新后重试");
        }
    }

    public PageResult<PurchaseVO> page(PurchaseQuery query) {
        var wrapper = Wrappers.<BizPurchase>lambdaQuery()
                .eq(query.getSupplierId() != null, BizPurchase::getSupplierId, query.getSupplierId())
                .eq(query.getWarehouseId() != null, BizPurchase::getWarehouseId, query.getWarehouseId())
                .eq(query.getStatus() != null, BizPurchase::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getPurchaseNo()),
                        BizPurchase::getPurchaseNo, query.getPurchaseNo() == null ? null : query.getPurchaseNo().trim())
                .orderByDesc(BizPurchase::getCreateTime);

        Page<BizPurchase> page = purchaseMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        Names names = loadNames(page.getRecords());
        return PageResult.of(page, p -> toVO(p, List.of(), names));
    }

    public PurchaseVO detail(Long id) {
        BizPurchase purchase = getExisting(id);
        List<BizPurchaseItem> items = itemMapper.selectList(Wrappers.<BizPurchaseItem>lambdaQuery()
                .eq(BizPurchaseItem::getPurchaseId, id));
        return toVO(purchase, items, loadNames(List.of(purchase)));
    }

    private BizPurchase getExisting(Long id) {
        BizPurchase purchase = purchaseMapper.selectById(id);
        if (purchase == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "采购单不存在");
        }
        return purchase;
    }

    private record Names(Map<Long, String> suppliers, Map<Long, String> warehouses,
                         Map<Long, String> users, Map<Long, BizDrug> drugs) {
    }

    private Names loadNames(List<BizPurchase> purchases) {
        Set<Long> supplierIds = purchases.stream().map(BizPurchase::getSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> warehouseIds = purchases.stream().map(BizPurchase::getWarehouseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = purchases.stream()
                .flatMap(p -> java.util.stream.Stream.of(p.getApplicantId(), p.getAuditorId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> suppliers = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            for (BizSupplier s : supplierMapper.selectByIds(supplierIds)) {
                suppliers.put(s.getId(), s.getSupplierName());
            }
        }
        Map<Long, String> warehouses = new HashMap<>();
        if (!warehouseIds.isEmpty()) {
            for (BizWarehouse w : warehouseMapper.selectByIds(warehouseIds)) {
                warehouses.put(w.getId(), w.getWarehouseName());
            }
        }
        Map<Long, String> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser u : userMapper.selectByIds(userIds)) {
                users.put(u.getId(), u.getRealName());
            }
        }
        return new Names(suppliers, warehouses, users, Map.of());
    }

    private PurchaseVO toVO(BizPurchase p, List<BizPurchaseItem> items, Names names) {
        Set<Long> drugIds = items.stream().map(BizPurchaseItem::getDrugId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizDrug> drugs = new HashMap<>();
        if (!drugIds.isEmpty()) {
            for (BizDrug d : drugMapper.selectByIds(drugIds)) {
                drugs.put(d.getId(), d);
            }
        }

        List<PurchaseItemVO> itemVOs = items.stream().map(i -> {
            BizDrug drug = drugs.get(i.getDrugId());
            return PurchaseItemVO.builder()
                    .id(String.valueOf(i.getId()))
                    .drugId(String.valueOf(i.getDrugId()))
                    .drugCode(drug == null ? null : drug.getDrugCode())
                    .drugName(drug == null ? null : drug.getDrugName())
                    .spec(drug == null ? null : drug.getSpec())
                    .unit(drug == null ? null : drug.getUnit())
                    .quantity(i.getQuantity())
                    .price(i.getPrice())
                    .amount(i.getAmount())
                    .arriveNum(i.getArriveNum())
                    .build();
        }).toList();

        return PurchaseVO.builder()
                .id(String.valueOf(p.getId()))
                .purchaseNo(p.getPurchaseNo())
                .supplierId(String.valueOf(p.getSupplierId()))
                .supplierName(names.suppliers().get(p.getSupplierId()))
                .warehouseId(String.valueOf(p.getWarehouseId()))
                .warehouseName(names.warehouses().get(p.getWarehouseId()))
                .totalAmount(p.getTotalAmount())
                .expectDate(p.getExpectDate())
                .status(p.getStatus())
                .applicantId(p.getApplicantId() == null ? null : String.valueOf(p.getApplicantId()))
                .applicantName(names.users().get(p.getApplicantId()))
                .auditorId(p.getAuditorId() == null ? null : String.valueOf(p.getAuditorId()))
                .auditorName(names.users().get(p.getAuditorId()))
                .auditTime(p.getAuditTime())
                .remark(p.getRemark())
                .createTime(p.getCreateTime())
                .items(itemVOs)
                .build();
    }
}
