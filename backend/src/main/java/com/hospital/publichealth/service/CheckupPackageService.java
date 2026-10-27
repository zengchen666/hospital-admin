package com.hospital.publichealth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.finance.entity.BizTreatItem;
import com.hospital.finance.mapper.BizTreatItemMapper;
import com.hospital.publichealth.dto.CheckupPackageRequest;
import com.hospital.publichealth.dto.CheckupPackageVO;
import com.hospital.publichealth.entity.BizCheckupPackage;
import com.hospital.publichealth.mapper.BizCheckupPackageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 体检套餐维护。
 *
 * <p><b>{@code itemIds} 的安全处理</b>：它是「数字 + 逗号」的纯文本，
 * 后续会被拆成 ID 列表去查诊疗项目。这里做两道校验：</p>
 * <ol>
 *   <li>字符白名单（只允许 {@code [0-9,]}）—— 挡住注入；</li>
 *   <li>解析后逐个 ID 查项目表，查不到的直接报错 —— 挡住手滑写错的项目。</li>
 * </ol>
 *
 * <p><b>停用不等于删除</b>：套餐一旦被体检单引用就删不掉（历史单要能还原当时买了什么），
 * 只能置为停用，新单不再选它。</p>
 */
@Slf4j
@Service
public class CheckupPackageService {

    private final BizCheckupPackageMapper packageMapper;
    private final BizTreatItemMapper treatItemMapper;

    public CheckupPackageService(BizCheckupPackageMapper packageMapper,
                                 BizTreatItemMapper treatItemMapper) {
        this.packageMapper = packageMapper;
        this.treatItemMapper = treatItemMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CheckupPackageRequest request) {
        validate(request);

        BizCheckupPackage entity = new BizCheckupPackage();
        entity.setPackageName(request.getPackageName().trim());
        entity.setItemIds(normalizeItemIds(request.getItemIds()));
        entity.setPrice(request.getPrice());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setCreateBy(CurrentUser.idOrNull());
        packageMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CheckupPackageRequest request) {
        BizCheckupPackage entity = requirePackage(id);
        validate(request);

        entity.setPackageName(request.getPackageName().trim());
        entity.setItemIds(normalizeItemIds(request.getItemIds()));
        entity.setPrice(request.getPrice());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setUpdateBy(CurrentUser.idOrNull());
        packageMapper.updateById(entity);
    }

    /** 启停：只改状态，不影响已生成的体检单（它们存的是名称与价格快照） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        requirePackage(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "状态只能是 0 停用 或 1 启用");
        }
        BizCheckupPackage entity = new BizCheckupPackage();
        entity.setId(id);
        entity.setStatus(status);
        entity.setUpdateBy(CurrentUser.idOrNull());
        packageMapper.updateById(entity);
    }

    public List<CheckupPackageVO> list(Boolean onlyEnabled, String keyword) {
        LambdaQueryWrapper<BizCheckupPackage> wrapper = Wrappers.<BizCheckupPackage>lambdaQuery()
                .eq(Boolean.TRUE.equals(onlyEnabled), BizCheckupPackage::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(BizCheckupPackage::getPackageName, keyword.trim());
        }
        wrapper.orderByAsc(BizCheckupPackage::getId);
        return packageMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public CheckupPackageVO detail(Long id) {
        return toVO(requirePackage(id));
    }

    private BizCheckupPackage requirePackage(Long id) {
        BizCheckupPackage entity = packageMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "体检套餐不存在");
        }
        return entity;
    }

    private void validate(CheckupPackageRequest request) {
        if (request.getPrice() == null || request.getPrice().signum() < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "套餐价不能为负");
        }
    }

    /**
     * 校验并规范化项目 ID 文本。
     *
     * @return 规范化后的文本；入参为空时返回 null
     */
    private String normalizeItemIds(String itemIds) {
        if (!StringUtils.hasText(itemIds)) {
            return null;
        }
        String trimmed = itemIds.trim();
        // 字符白名单：这串文本会被拆成 ID 用，放行任意字符等于开注入口子
        if (!trimmed.matches("[0-9,]+")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "项目 ID 只能包含数字与逗号");
        }
        List<Long> ids = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            if (!part.isBlank()) {
                ids.add(Long.parseLong(part));
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        // 逐个校验项目真实存在，避免手滑写错 ID 后体检单里出现空项目
        for (Long itemId : ids) {
            if (treatItemMapper.selectById(itemId) == null) {
                throw new BusinessException(ErrorCode.NO_RESOURCE, "诊疗项目 " + itemId + " 不存在");
            }
        }
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private CheckupPackageVO toVO(BizCheckupPackage entity) {
        return CheckupPackageVO.builder()
                .id(String.valueOf(entity.getId()))
                .packageName(entity.getPackageName())
                .itemIds(entity.getItemIds())
                .itemNames(resolveItemNames(entity.getItemIds()))
                .price(entity.getPrice())
                .status(entity.getStatus())
                .build();
    }

    private List<String> resolveItemNames(String itemIds) {
        if (!StringUtils.hasText(itemIds)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : itemIds.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            BizTreatItem item = treatItemMapper.selectById(Long.parseLong(part));
            names.add(item == null ? part : item.getItemName());
        }
        return names;
    }

}
