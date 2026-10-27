package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.pharmacy.dto.DrugQuery;
import com.hospital.pharmacy.dto.DrugRequest;
import com.hospital.pharmacy.dto.DrugVO;
import com.hospital.pharmacy.dto.StockInRequest;
import com.hospital.pharmacy.dto.StockOutRequest;
import com.hospital.pharmacy.dto.StockQuery;
import com.hospital.pharmacy.dto.StockVO;
import com.hospital.pharmacy.dto.WarehouseRequest;
import com.hospital.pharmacy.dto.WarehouseVO;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.entity.BizStock;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.entity.BizWarehouse;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 药品字典、库存、库房。
 *
 * <p><b>库存变动的三条铁律</b>：</p>
 * <ol>
 *   <li>扣减走条件更新（{@code quantity >= n}），不做「先查后改」；</li>
 *   <li>每一次变动都写 {@link BizStockRecord}，且与库存变更同事务；</li>
 *   <li>批号必填 —— 没有批号的库存无法追溯，召回时只能整库报废。</li>
 * </ol>
 *
 * <p>药品删除走逻辑删除：药品带着历史处方与发药记录，
 * 物理删掉会让老单据上的 drug_id 变成悬空引用。</p>
 */
@Slf4j
@Service
public class DrugService {

    /** 入库 / 出库 / 盘点 / 退货 */
    private static final int RECORD_IN = 1;
    private static final int RECORD_OUT = 2;

    /** 效期预警默认窗口（天） */
    private static final int DEFAULT_EXPIRE_DAYS = 90;

    private final BizDrugMapper drugMapper;
    private final BizStockMapper stockMapper;
    private final BizStockRecordMapper recordMapper;
    private final BizWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;

    public DrugService(BizDrugMapper drugMapper,
                       BizStockMapper stockMapper,
                       BizStockRecordMapper recordMapper,
                       BizWarehouseMapper warehouseMapper,
                       SysUserMapper userMapper) {
        this.drugMapper = drugMapper;
        this.stockMapper = stockMapper;
        this.recordMapper = recordMapper;
        this.warehouseMapper = warehouseMapper;
        this.userMapper = userMapper;
    }

    /* ---------------- 药品字典 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public Long createDrug(DrugRequest request) {
        checkCodeUnique(request.getDrugCode(), null);
        BizDrug drug = toEntity(request);
        drugMapper.insert(drug);
        log.info("新增药品 drugCode={} name={}", drug.getDrugCode(), drug.getDrugName());
        return drug.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDrug(Long id, DrugRequest request) {
        BizDrug existing = getDrug(id);
        if (!Objects.equals(existing.getDrugCode(), request.getDrugCode())) {
            checkCodeUnique(request.getDrugCode(), id);
        }
        BizDrug drug = toEntity(request);
        drug.setId(id);
        drugMapper.updateById(drug);
        log.info("修改药品 id={} drugCode={}", id, drug.getDrugCode());
    }

    /**
     * 删除药品（逻辑删除）。
     *
     * <p>有库存的药品不允许停用/删除 —— 药还在架子上却查不到，
     * 发药时选不出来、盘点时对不上账。先清库存再删。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDrug(Long id) {
        BizDrug drug = getDrug(id);
        Long stockSum = stockMapper.selectList(Wrappers.<BizStock>lambdaQuery()
                        .eq(BizStock::getDrugId, id))
                .stream().mapToLong(s -> s.getQuantity() == null ? 0 : s.getQuantity()).sum();
        if (stockSum > 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该药品仍有库存 %d，请先处理库存再删除", stockSum);
        }
        drugMapper.deleteById(id);
        log.info("删除药品 id={} drugCode={}", id, drug.getDrugCode());
    }

    public PageResult<DrugVO> pageDrugs(DrugQuery query) {
        String keyword = query.getKeyword();
        boolean hasKeyword = StringUtils.hasText(keyword);

        LambdaQueryWrapper<BizDrug> wrapper = Wrappers.<BizDrug>lambdaQuery()
                .eq(query.getStatus() != null, BizDrug::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCategory()), BizDrug::getCategory, query.getCategory())
                .eq(Boolean.TRUE.equals(query.getOnlySpecial()), BizDrug::getIsSpecial, 1);

        if (hasKeyword) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(BizDrug::getDrugName, kw)
                    .or().like(BizDrug::getGenericName, kw)
                    .or().like(BizDrug::getDrugCode, kw));
        }
        wrapper.orderByAsc(BizDrug::getDrugCode);

        Page<BizDrug> page = drugMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResult.of(page, d -> DrugVO.builder().build());
        }
        Map<Long, Integer> stockSum = sumStock(page.getRecords());
        return PageResult.of(page, d -> toDrugVO(d, stockSum.get(d.getId())));
    }

    /* ---------------- 库存 ---------------- */

    public PageResult<StockVO> pageStock(StockQuery query) {
        LambdaQueryWrapper<BizStock> wrapper = Wrappers.<BizStock>lambdaQuery()
                .eq(query.getDrugId() != null, BizStock::getDrugId, query.getDrugId())
                .eq(query.getWarehouseId() != null, BizStock::getWarehouseId, query.getWarehouseId());

        if (StringUtils.hasText(query.getKeyword())) {
            List<Long> drugIds = drugMapper.selectList(Wrappers.<BizDrug>lambdaQuery()
                            .like(BizDrug::getDrugName, query.getKeyword().trim())
                            .or().like(BizDrug::getDrugCode, query.getKeyword().trim()))
                    .stream().map(BizDrug::getId).toList();
            if (drugIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(BizStock::getDrugId, drugIds);
        }
        // 预警 / 效期过滤必须下推到 SQL，不能在内存里过滤 ——
        // 内存过滤会让分页「总数对、内容不对」：第 1 页 20 条过滤完只剩 3 条，
        // 用户看到的是「共 200 条」配一个半空的表格
        if (Boolean.TRUE.equals(query.getOnlyWarn())) {
            wrapper.apply("quantity <= warn_num AND warn_num > 0");
        }
        int expireDays = DEFAULT_EXPIRE_DAYS;
        if (query.getExpireDays() != null) {
            expireDays = query.getExpireDays();
            wrapper.le(BizStock::getExpireDate, LocalDate.now().plusDays(expireDays));
        }
        wrapper.orderByAsc(BizStock::getExpireDate).orderByAsc(BizStock::getId);

        Page<BizStock> page = stockMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<BizStock> rows = page.getRecords();
        if (rows.isEmpty()) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }

        LocalDate today = LocalDate.now();
        Map<Long, BizDrug> drugs = loadDrugs(rows);
        Map<Long, BizWarehouse> warehouses = loadWarehouses(rows);
        int days = expireDays;
        return PageResult.of(page, s -> toStockVO(s, drugs, warehouses, today, days));
    }

    /** 库存预警：低于预警线的批次 */
    public List<StockVO> warnList() {
        List<BizStock> all = stockMapper.selectList(Wrappers.<BizStock>lambdaQuery()
                .orderByAsc(BizStock::getExpireDate));
        LocalDate today = LocalDate.now();
        List<BizStock> warned = all.stream().filter(this::isWarn).toList();
        Map<Long, BizDrug> drugs = loadDrugs(warned);
        Map<Long, BizWarehouse> warehouses = loadWarehouses(warned);
        return warned.stream()
                .map(s -> toStockVO(s, drugs, warehouses, today, DEFAULT_EXPIRE_DAYS))
                .toList();
    }

    /** 效期预警：已过期或即将过期 */
    public List<StockVO> expireList(Integer expireDays) {
        int days = expireDays == null ? DEFAULT_EXPIRE_DAYS : expireDays;
        LocalDate limit = LocalDate.now().plusDays(days);
        List<BizStock> all = stockMapper.selectList(Wrappers.<BizStock>lambdaQuery()
                .orderByAsc(BizStock::getExpireDate));
        List<BizStock> expiring = all.stream()
                .filter(s -> s.getExpireDate() != null && !s.getExpireDate().isAfter(limit))
                .toList();
        Map<Long, BizDrug> drugs = loadDrugs(expiring);
        Map<Long, BizWarehouse> warehouses = loadWarehouses(expiring);
        return expiring.stream()
                .map(s -> toStockVO(s, drugs, warehouses, LocalDate.now(), days))
                .toList();
    }

    /**
     * 入库。
     *
     * <p>同一 药品 + 库房 + 批号 已有记录时<b>累加</b>而不是新建一行：
     * 同一个批次的药本来就是一回事，拆成两行会让发药时按批号扣减扣错对象。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(StockInRequest request) {
        validateStockIn(request);
        getDrug(request.getDrugId());
        getWarehouse(request.getWarehouseId());

        BizStock existing = stockMapper.selectOne(Wrappers.<BizStock>lambdaQuery()
                .eq(BizStock::getDrugId, request.getDrugId())
                .eq(BizStock::getWarehouseId, request.getWarehouseId())
                .eq(BizStock::getBatchNo, request.getBatchNo()));

        if (existing == null) {
            BizStock stock = new BizStock();
            stock.setDrugId(request.getDrugId());
            stock.setWarehouseId(request.getWarehouseId());
            stock.setBatchNo(request.getBatchNo());
            stock.setQuantity(request.getQuantity());
            stock.setWarnNum(request.getWarnNum() == null ? 0 : request.getWarnNum());
            stock.setProduceDate(request.getProduceDate());
            stock.setExpireDate(request.getExpireDate());
            stockMapper.insert(stock);
        } else {
            BizStock update = new BizStock();
            update.setId(existing.getId());
            update.setQuantity(existing.getQuantity() + request.getQuantity());
            if (request.getProduceDate() != null) {
                update.setProduceDate(request.getProduceDate());
            }
            update.setExpireDate(request.getExpireDate());
            if (request.getWarnNum() != null) {
                update.setWarnNum(request.getWarnNum());
            }
            stockMapper.updateById(update);
        }

        writeRecord(request.getDrugId(), request.getWarehouseId(), RECORD_IN,
                request.getQuantity(), request.getBatchNo(), request.getRelatedNo(), request.getRemark());
        log.info("入库 drugId={} warehouseId={} batch={} qty={}",
                request.getDrugId(), request.getWarehouseId(), request.getBatchNo(), request.getQuantity());
    }

    /**
     * 出库（报损 / 领用，非发药）。
     *
     * <p>发药不走这里 —— 发药还要写发药单、推进处方状态，走 DispenseService。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(StockOutRequest request) {
        BizStock stock = stockMapper.selectOne(Wrappers.<BizStock>lambdaQuery()
                .eq(BizStock::getDrugId, request.getDrugId())
                .eq(BizStock::getWarehouseId, request.getWarehouseId())
                .eq(BizStock::getBatchNo, request.getBatchNo()));
        if (stock == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "该批号库存不存在");
        }
        if (stockMapper.decrease(stock.getId(), request.getQuantity()) == 0) {
            throw BusinessException.of(ErrorCode.STOCK_NOT_ENOUGH, "库存不足，当前余量 %d",
                    stock.getQuantity() == null ? 0 : stock.getQuantity());
        }
        writeRecord(request.getDrugId(), request.getWarehouseId(), RECORD_OUT,
                request.getQuantity(), request.getBatchNo(), request.getRelatedNo(), request.getRemark());
        log.info("出库 drugId={} batch={} qty={}", request.getDrugId(), request.getBatchNo(), request.getQuantity());
    }

    /* ---------------- 库房 ---------------- */

    public List<WarehouseVO> warehouses() {
        List<BizWarehouse> list = warehouseMapper.selectList(Wrappers.<BizWarehouse>lambdaQuery()
                .orderByAsc(BizWarehouse::getWarehouseCode));
        return list.stream().map(this::toWarehouseVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createWarehouse(WarehouseRequest request) {
        Long count = warehouseMapper.selectCount(Wrappers.<BizWarehouse>lambdaQuery()
                .eq(BizWarehouse::getWarehouseCode, request.getWarehouseCode()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "库房编码已存在");
        }
        BizWarehouse warehouse = new BizWarehouse();
        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setWarehouseCode(request.getWarehouseCode());
        warehouse.setWarehouseType(request.getWarehouseType());
        warehouse.setDeptId(request.getDeptId());
        warehouse.setManagerId(request.getManagerId());
        warehouse.setStatus(1);
        warehouseMapper.insert(warehouse);
        return warehouse.getId();
    }

    /* ---------------- 私有 ---------------- */

    private void validateStockIn(StockInRequest request) {
        if (request.getExpireDate() == null) {
            return;
        }
        if (!request.getExpireDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "有效期必须晚于今天，已过期的批次不能入库");
        }
        if (request.getProduceDate() != null && !request.getExpireDate().isAfter(request.getProduceDate())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "有效期必须晚于生产日期");
        }
    }

    private void writeRecord(Long drugId, Long warehouseId, Integer type,
                             Integer quantity, String batchNo, String relatedNo, String remark) {
        BizStockRecord record = new BizStockRecord();
        record.setDrugId(drugId);
        record.setWarehouseId(warehouseId);
        record.setRecordType(type);
        record.setQuantity(quantity);
        record.setBatchNo(batchNo);
        record.setRelatedNo(relatedNo);
        record.setOperatorId(CurrentUser.idOrNull());
        record.setRemark(remark);
        recordMapper.insert(record);
    }

    private BizDrug getDrug(Long id) {
        BizDrug drug = drugMapper.selectById(id);
        if (drug == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "药品不存在");
        }
        return drug;
    }

    private BizWarehouse getWarehouse(Long id) {
        BizWarehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "库房不存在");
        }
        return warehouse;
    }

    private void checkCodeUnique(String drugCode, Long excludeId) {
        LambdaQueryWrapper<BizDrug> wrapper = Wrappers.<BizDrug>lambdaQuery()
                .eq(BizDrug::getDrugCode, drugCode);
        if (excludeId != null) {
            wrapper.ne(BizDrug::getId, excludeId);
        }
        Long count = drugMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BusinessException.of(ErrorCode.DUPLICATE_DATA, "药品编码 %s 已存在", drugCode);
        }
    }

    private BizDrug toEntity(DrugRequest request) {
        BizDrug drug = new BizDrug();
        drug.setDrugCode(request.getDrugCode());
        drug.setDrugName(request.getDrugName());
        drug.setGenericName(request.getGenericName());
        drug.setSpec(request.getSpec());
        drug.setDosageForm(request.getDosageForm());
        drug.setUnit(request.getUnit());
        drug.setManufacturer(request.getManufacturer());
        drug.setApprovalNo(request.getApprovalNo());
        drug.setPurchasePrice(request.getPurchasePrice());
        drug.setSalePrice(request.getSalePrice());
        drug.setCategory(request.getCategory());
        drug.setIsSpecial(request.getIsSpecial() == null ? 0 : request.getIsSpecial());
        drug.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (request.getPurchasePrice() != null && request.getSalePrice() != null
                && request.getSalePrice().compareTo(request.getPurchasePrice()) < 0) {
            // 政策性亏损药品确实存在（部分急救药），不阻断业务，但要留痕
            log.warn("药品售价低于进价 drugCode={} purchase={} sale={}",
                    request.getDrugCode(), request.getPurchasePrice(), request.getSalePrice());
        }
        return drug;
    }

    private DrugVO toDrugVO(BizDrug drug, Integer stock) {
        return DrugVO.builder()
                .id(String.valueOf(drug.getId()))
                .drugCode(drug.getDrugCode())
                .drugName(drug.getDrugName())
                .genericName(drug.getGenericName())
                .spec(drug.getSpec())
                .dosageForm(drug.getDosageForm())
                .unit(drug.getUnit())
                .manufacturer(drug.getManufacturer())
                .approvalNo(drug.getApprovalNo())
                .purchasePrice(drug.getPurchasePrice())
                .salePrice(drug.getSalePrice())
                .category(drug.getCategory())
                .isSpecial(drug.getIsSpecial())
                .status(drug.getStatus())
                .totalStock(stock)
                .build();
    }

    private Map<Long, Integer> sumStock(List<BizDrug> drugs) {
        Set<Long> drugIds = drugs.stream().map(BizDrug::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (drugIds.isEmpty()) {
            return Map.of();
        }
        List<BizStock> stocks = stockMapper.selectList(
                Wrappers.<BizStock>lambdaQuery().in(BizStock::getDrugId, drugIds));
        Map<Long, Integer> sum = new HashMap<>();
        stocks.forEach(s -> sum.merge(s.getDrugId(),
                s.getQuantity() == null ? 0 : s.getQuantity(), Integer::sum));
        return sum;
    }

    private Map<Long, BizDrug> loadDrugs(List<BizStock> stocks) {
        Set<Long> ids = stocks.stream().map(BizStock::getDrugId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BizDrug> map = new HashMap<>();
        for (BizDrug d : drugMapper.selectByIds(ids)) {
            map.put(d.getId(), d);
        }
        return map;
    }

    private Map<Long, BizWarehouse> loadWarehouses(List<BizStock> stocks) {
        Set<Long> ids = stocks.stream().map(BizStock::getWarehouseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BizWarehouse> map = new HashMap<>();
        for (BizWarehouse w : warehouseMapper.selectByIds(ids)) {
            map.put(w.getId(), w);
        }
        return map;
    }

    private boolean isWarn(BizStock stock) {
        Integer warn = stock.getWarnNum();
        Integer qty = stock.getQuantity();
        return warn != null && warn > 0 && qty != null && qty <= warn;
    }

    private StockVO toStockVO(BizStock stock, Map<Long, BizDrug> drugs,
                              Map<Long, BizWarehouse> warehouses, LocalDate today, int expireDays) {
        BizDrug drug = drugs.get(stock.getDrugId());
        BizWarehouse warehouse = warehouses.get(stock.getWarehouseId());
        Long days = stock.getExpireDate() == null ? null
                : ChronoUnit.DAYS.between(today, stock.getExpireDate());
        boolean expiring = days != null && days <= expireDays;

        return StockVO.builder()
                .id(String.valueOf(stock.getId()))
                .drugId(String.valueOf(stock.getDrugId()))
                .drugCode(drug == null ? null : drug.getDrugCode())
                .drugName(drug == null ? null : drug.getDrugName())
                .spec(drug == null ? null : drug.getSpec())
                .unit(drug == null ? null : drug.getUnit())
                .warehouseId(String.valueOf(stock.getWarehouseId()))
                .warehouseName(warehouse == null ? null : warehouse.getWarehouseName())
                .batchNo(stock.getBatchNo())
                .quantity(stock.getQuantity())
                .warnNum(stock.getWarnNum())
                .produceDate(stock.getProduceDate())
                .expireDate(stock.getExpireDate())
                .warn(isWarn(stock))
                .expiring(expiring)
                .daysToExpire(days)
                .build();
    }

    private WarehouseVO toWarehouseVO(BizWarehouse w) {
        String managerName = null;
        if (w.getManagerId() != null) {
            SysUser user = userMapper.selectById(w.getManagerId());
            managerName = user == null ? null : user.getRealName();
        }
        return WarehouseVO.builder()
                .id(String.valueOf(w.getId()))
                .warehouseName(w.getWarehouseName())
                .warehouseCode(w.getWarehouseCode())
                .warehouseType(w.getWarehouseType())
                .deptId(w.getDeptId() == null ? null : String.valueOf(w.getDeptId()))
                .managerId(w.getManagerId() == null ? null : String.valueOf(w.getManagerId()))
                .managerName(managerName)
                .status(w.getStatus())
                .build();
    }
}
