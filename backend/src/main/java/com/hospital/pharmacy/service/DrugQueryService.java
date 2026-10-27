package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.pharmacy.dto.DrugOptionVO;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 药品查询（只读）。
 *
 * <p>本期只为门诊开方提供选药列表，药品维护/库存/发药在药房模块补齐。</p>
 */
@Service
public class DrugQueryService {

    private static final int MAX_OPTIONS = 50;

    private final BizDrugMapper drugMapper;

    public DrugQueryService(BizDrugMapper drugMapper) {
        this.drugMapper = drugMapper;
    }

    /**
     * 药品选项：只返回启用中的药品，最多 50 条。
     *
     * <p>限制条数不是偷懒：药品字典通常上千条，一次性全返回会让开方弹窗卡死，
     * 正确做法是让前端带关键字搜索。</p>
     */
    public List<DrugOptionVO> options(String keyword) {
        var wrapper = Wrappers.<BizDrug>lambdaQuery()
                .eq(BizDrug::getStatus, 1);

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(BizDrug::getDrugName, kw)
                    .or().like(BizDrug::getGenericName, kw)
                    .or().like(BizDrug::getDrugCode, kw));
        }
        wrapper.orderByAsc(BizDrug::getDrugName).last("LIMIT " + MAX_OPTIONS);

        return drugMapper.selectList(wrapper).stream()
                .map(drug -> DrugOptionVO.builder()
                        .id(String.valueOf(drug.getId()))
                        .drugCode(drug.getDrugCode())
                        .drugName(drug.getDrugName())
                        .spec(drug.getSpec())
                        .unit(drug.getUnit())
                        .salePrice(drug.getSalePrice())
                        .category(drug.getCategory())
                        .isSpecial(drug.getIsSpecial())
                        .build())
                .toList();
    }
}
