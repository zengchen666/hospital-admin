package com.hospital.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.pharmacy.entity.BizDrug;

/**
 * 药品字典 Mapper。
 *
 * <p>开方时用 {@code selectByIds} 一次性把本张处方涉及的药品全查出来，
 * 不要在循环里逐个 {@code selectById}。</p>
 */
public interface BizDrugMapper extends BaseMapper<BizDrug> {
}
