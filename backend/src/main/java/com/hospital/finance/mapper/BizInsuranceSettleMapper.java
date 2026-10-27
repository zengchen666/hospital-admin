package com.hospital.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.finance.entity.BizInsuranceSettle;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizInsuranceSettleMapper extends BaseMapper<BizInsuranceSettle> {

    /**
     * 按收费单查最近一次结算记录。
     *
     * <p>一张收费单可能结算过多次（失败重试），取 ID 最大的那次：
     * 业务关心的是「现在到底结算了没有」，不是第一次尝试的结果。</p>
     */
    @Select("""
            SELECT * FROM biz_insurance_settle
             WHERE charge_id = #{chargeId}
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    BizInsuranceSettle selectLatestByCharge(@Param("chargeId") Long chargeId);
}
