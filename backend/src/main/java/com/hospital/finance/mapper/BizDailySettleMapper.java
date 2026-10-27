package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizDailySettle;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface BizDailySettleMapper extends BaseMapper<BizDailySettle> {

    @Select("""
            SELECT * FROM biz_daily_settle
             WHERE settle_date = #{settleDate}
               AND deleted = 0
             LIMIT 1
            """)
    BizDailySettle selectByDate(@Param("settleDate") LocalDate settleDate);
}
