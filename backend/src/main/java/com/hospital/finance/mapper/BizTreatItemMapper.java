package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizTreatItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizTreatItemMapper extends BaseMapper<BizTreatItem> {

    /**
     * 取项目当前生效价。
     *
     * <p>同一编码可能有多个价格版本（uk_treat_code 是 item_code + effective_date），
     * 这里取「已生效且日期最新」的一版。<b>不能只按 item_code 查</b> ——
     * 那会命中任意一版价格。</p>
     */
    @Select("""
            SELECT * FROM biz_treat_item
             WHERE item_code = #{itemCode}
               AND status = 1
               AND deleted = 0
               AND effective_date <= CURDATE()
             ORDER BY effective_date DESC
             LIMIT 1
            """)
    BizTreatItem selectEffective(@Param("itemCode") String itemCode);
}
