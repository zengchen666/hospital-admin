package com.hospital.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.exam.entity.BizExamItem;

/**
 * 检验项目明细 Mapper。查询统一 {@code in(applyId)} 批量取。
 */
public interface BizExamItemMapper extends BaseMapper<BizExamItem> {
}
