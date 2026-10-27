package com.hospital.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.clinic.entity.BizPrescriptionItem;

/**
 * 处方明细 Mapper。
 *
 * <p>查询统一走 {@code selectList(in(prescId))} 批量取，
 * 不要在循环里一条处方查一次明细 —— 一页 20 张处方就是 20 次往返。</p>
 */
public interface BizPrescriptionItemMapper extends BaseMapper<BizPrescriptionItem> {
}
