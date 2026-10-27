package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.finance.dto.TreatItemQuery;
import com.hospital.finance.dto.TreatItemRequest;
import com.hospital.finance.dto.TreatItemVO;
import com.hospital.finance.entity.BizTreatItem;
import com.hospital.finance.mapper.BizTreatItemMapper;
import com.hospital.system.entity.SysDept;
import com.hospital.system.mapper.SysDeptMapper;
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
import java.util.stream.Collectors;

/**
 * 诊疗项目价格（价目表）服务。
 *
 * <p><b>调价 = 新增一版，不是覆盖旧价</b>。理由很实在：
 * 上月做的检查按上月的价收，患者来查、医保来核，都得拿得到当时的价。
 * 直接 UPDATE 掉旧价，历史收费单就成了「金额对不上任何一版价目表」的悬案。</p>
 *
 * <p>取价永远走 {@code effective_date <= 今天} 的最新一版 ——
 * 允许提前录入未来生效价，到点自动切换，不用半夜上线调价。</p>
 */
@Slf4j
@Service
public class TreatItemService {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final BizTreatItemMapper treatItemMapper;
    private final SysDeptMapper deptMapper;

    public TreatItemService(BizTreatItemMapper treatItemMapper, SysDeptMapper deptMapper) {
        this.treatItemMapper = treatItemMapper;
        this.deptMapper = deptMapper;
    }

    public PageResult<TreatItemVO> page(TreatItemQuery query) {
        LambdaQueryWrapper<BizTreatItem> wrapper = Wrappers.<BizTreatItem>lambdaQuery()
                .eq(query.getItemType() != null, BizTreatItem::getItemType, query.getItemType())
                .eq(query.getStatus() != null, BizTreatItem::getStatus, query.getStatus())
                .eq(query.getDeptId() != null, BizTreatItem::getDeptId, query.getDeptId());

        String keyword = query.getKeyword();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.likeRight(BizTreatItem::getItemCode, kw)
                    .or().like(BizTreatItem::getItemName, kw));
        }
        if (Boolean.TRUE.equals(query.getOnlyEffective())) {
            // 只看「现在正在用」的价：不晚于今天、未停用
            wrapper.le(BizTreatItem::getEffectiveDate, LocalDate.now())
                    .eq(BizTreatItem::getStatus, STATUS_ENABLED);
        }
        wrapper.orderByAsc(BizTreatItem::getItemCode)
                .orderByDesc(BizTreatItem::getEffectiveDate);

        Page<BizTreatItem> page = treatItemMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> depts = loadDeptNames(page.getRecords());
        // 同一编码的行数 > 1 才算有历史版本
        Map<String, Long> versionCount = countVersions(page.getRecords());
        return PageResult.of(page, e -> toVO(e, depts, versionCount));
    }

    public TreatItemVO detail(Long id) {
        BizTreatItem entity = treatItemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "诊疗项目不存在");
        }
        return toVO(entity, loadDeptNames(List.of(entity)), countVersions(List.of(entity)));
    }

    /**
     * 新增项目。
     *
     * <p>同一编码 + 同一生效日期只能有一版（库上有唯一键），
     * 这里先查一次给出人话提示，别让前端去翻译 MySQL 的 1062。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(TreatItemRequest request) {
        LocalDate effective = request.getEffectiveDate() == null
                ? LocalDate.now() : request.getEffectiveDate();
        assertNoDuplicate(request.getItemCode().trim(), effective, null);

        BizTreatItem entity = new BizTreatItem();
        apply(entity, request, effective);
        treatItemMapper.insert(entity);
        log.info("新增诊疗项目 code={} price={} effective={} operator={}",
                entity.getItemCode(), entity.getPrice(), effective, CurrentUser.idOrNull());
        return entity.getId();
    }

    /**
     * 调价：新增一版价格记录，原记录原样保留。
     *
     * <p>只有「名称/规格/单位/医保属性」这类非价格字段才走 UPDATE ——
     * 它们不参与历史计费，改了不会让旧单对不上账。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long adjustPrice(Long id, TreatItemRequest request) {
        BizTreatItem existing = treatItemMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "诊疗项目不存在");
        }

        boolean priceChanged = existing.getPrice() == null
                || existing.getPrice().compareTo(nvl(request.getPrice())) != 0;
        LocalDate effective = request.getEffectiveDate() == null
                ? LocalDate.now() : request.getEffectiveDate();

        if (!priceChanged && effective.equals(existing.getEffectiveDate())) {
            // 价格没动：按普通字段维护处理，不产生新版本
            apply(existing, request, effective);
            treatItemMapper.updateById(existing);
            log.info("维护诊疗项目 code={} id={}", existing.getItemCode(), id);
            return id;
        }

        assertNoDuplicate(existing.getItemCode(), effective, id);
        BizTreatItem newVersion = new BizTreatItem();
        apply(newVersion, request, effective);
        // 编码沿用原项目：调价不是换项目
        newVersion.setItemCode(existing.getItemCode());
        treatItemMapper.insert(newVersion);
        log.info("诊疗项目调价 code={} {} -> {} 生效 {} operator={}",
                existing.getItemCode(), existing.getPrice(), newVersion.getPrice(),
                effective, CurrentUser.idOrNull());
        return newVersion.getId();
    }

    /** 停用：不删除。已开出去的单还要靠它查历史价 */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        BizTreatItem entity = treatItemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "诊疗项目不存在");
        }
        BizTreatItem update = new BizTreatItem();
        update.setId(id);
        update.setStatus(STATUS_DISABLED);
        treatItemMapper.updateById(update);
        log.info("停用诊疗项目 code={} id={}", entity.getItemCode(), id);
    }

    private void apply(BizTreatItem entity, TreatItemRequest request, LocalDate effective) {
        if (entity.getId() == null || StringUtils.hasText(request.getItemCode())) {
            entity.setItemCode(request.getItemCode() == null
                    ? entity.getItemCode() : request.getItemCode().trim());
        }
        entity.setItemName(request.getItemName().trim());
        entity.setItemType(request.getItemType());
        entity.setSpec(request.getSpec());
        entity.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit() : "次");
        entity.setPrice(nvl(request.getPrice()));
        entity.setInsuranceType(request.getInsuranceType() == null ? 3 : request.getInsuranceType());
        entity.setInsuranceRatio(request.getInsuranceRatio() == null
                ? BigDecimal.ZERO : request.getInsuranceRatio());
        entity.setDeptId(request.getDeptId());
        entity.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        entity.setEffectiveDate(effective);
    }

    private void assertNoDuplicate(String itemCode, LocalDate effective, Long excludeId) {
        Long count = treatItemMapper.selectCount(Wrappers.<BizTreatItem>lambdaQuery()
                .eq(BizTreatItem::getItemCode, itemCode)
                .eq(BizTreatItem::getEffectiveDate, effective)
                .ne(excludeId != null, BizTreatItem::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA,
                    "该项目在 " + effective + " 已有价格版本，请换一个生效日期");
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<Long, String> loadDeptNames(List<BizTreatItem> items) {
        List<Long> deptIds = items.stream().map(BizTreatItem::getDeptId)
                .filter(Objects::nonNull).distinct().toList();
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysDept dept : deptMapper.selectByIds(deptIds)) {
            names.put(dept.getId(), dept.getDeptName());
        }
        return names;
    }

    /** 统计这些行所属编码一共有几个版本（只在本页内统计，够用于「是否可展开历史」提示） */
    private Map<String, Long> countVersions(List<BizTreatItem> items) {
        List<String> codes = items.stream().map(BizTreatItem::getItemCode)
                .filter(StringUtils::hasText).distinct().toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        return treatItemMapper.selectList(Wrappers.<BizTreatItem>lambdaQuery()
                        .in(BizTreatItem::getItemCode, codes))
                .stream().collect(Collectors.groupingBy(BizTreatItem::getItemCode, Collectors.counting()));
    }

    private TreatItemVO toVO(BizTreatItem entity,
                             Map<Long, String> depts,
                             Map<String, Long> versionCount) {
        return TreatItemVO.builder()
                .id(String.valueOf(entity.getId()))
                .itemCode(entity.getItemCode())
                .itemName(entity.getItemName())
                .itemType(entity.getItemType())
                .spec(entity.getSpec())
                .unit(entity.getUnit())
                .price(entity.getPrice())
                .insuranceType(entity.getInsuranceType())
                .insuranceRatio(entity.getInsuranceRatio())
                .deptId(entity.getDeptId())
                // deptId 可能为 null，而 Map.of() 的 get(null) 会抛 NPE ——
                // 不可变空 Map 对 null key 是「拒绝」而不是「返回 null」
                .deptName(entity.getDeptId() == null ? null : depts.get(entity.getDeptId()))
                .status(entity.getStatus())
                .effectiveDate(entity.getEffectiveDate())
                .hasHistory(entity.getItemCode() != null
                        && versionCount.getOrDefault(entity.getItemCode(), 1L) > 1)
                .build();
    }
}
