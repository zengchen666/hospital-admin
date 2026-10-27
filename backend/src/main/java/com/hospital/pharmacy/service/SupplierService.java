package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.pharmacy.dto.SupplierQuery;
import com.hospital.pharmacy.dto.SupplierRequest;
import com.hospital.pharmacy.dto.SupplierVO;
import com.hospital.pharmacy.entity.BizSupplier;
import com.hospital.pharmacy.mapper.BizSupplierMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 供应商维护。
 *
 * <p>只做启停、不做物理删除：供应商挂着历史采购单，
 * 删了以后老单据上的 supplier_id 就指不到人，查不清货是谁供的。</p>
 */
@Slf4j
@Service
public class SupplierService {

    private final BizSupplierMapper supplierMapper;

    public SupplierService(BizSupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierRequest request) {
        checkCodeUnique(request.getSupplierCode(), null);
        BizSupplier supplier = toEntity(request);
        supplierMapper.insert(supplier);
        log.info("新增供应商 code={} name={}", supplier.getSupplierCode(), supplier.getSupplierName());
        return supplier.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SupplierRequest request) {
        BizSupplier existing = getExisting(id);
        if (!Objects.equals(existing.getSupplierCode(), request.getSupplierCode())) {
            checkCodeUnique(request.getSupplierCode(), id);
        }
        BizSupplier supplier = toEntity(request);
        supplier.setId(id);
        supplierMapper.updateById(supplier);
    }

    public PageResult<SupplierVO> page(SupplierQuery query) {
        var wrapper = Wrappers.<BizSupplier>lambdaQuery()
                .eq(query.getStatus() != null, BizSupplier::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(BizSupplier::getSupplierName, kw)
                    .or().like(BizSupplier::getSupplierCode, kw)
                    .or().like(BizSupplier::getContactName, kw));
        }
        wrapper.orderByAsc(BizSupplier::getSupplierCode);

        Page<BizSupplier> page = supplierMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    public SupplierVO detail(Long id) {
        return toVO(getExisting(id));
    }

    private BizSupplier getExisting(Long id) {
        BizSupplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "供应商不存在");
        }
        return supplier;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        var wrapper = Wrappers.<BizSupplier>lambdaQuery().eq(BizSupplier::getSupplierCode, code);
        if (excludeId != null) {
            wrapper.ne(BizSupplier::getId, excludeId);
        }
        Long count = supplierMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BusinessException.of(ErrorCode.DUPLICATE_DATA, "供应商编码 %s 已存在", code);
        }
    }

    private BizSupplier toEntity(SupplierRequest request) {
        BizSupplier supplier = new BizSupplier();
        supplier.setSupplierName(request.getSupplierName());
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setContactName(request.getContactName());
        supplier.setContactPhone(request.getContactPhone());
        supplier.setAddress(request.getAddress());
        supplier.setLicenseNo(request.getLicenseNo());
        supplier.setCreditLevel(request.getCreditLevel() == null ? 3 : request.getCreditLevel());
        supplier.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        return supplier;
    }

    private SupplierVO toVO(BizSupplier s) {
        return SupplierVO.builder()
                .id(String.valueOf(s.getId()))
                .supplierName(s.getSupplierName())
                .supplierCode(s.getSupplierCode())
                .contactName(s.getContactName())
                .contactPhone(s.getContactPhone())
                .address(s.getAddress())
                .licenseNo(s.getLicenseNo())
                .creditLevel(s.getCreditLevel())
                .status(s.getStatus())
                .build();
    }
}
