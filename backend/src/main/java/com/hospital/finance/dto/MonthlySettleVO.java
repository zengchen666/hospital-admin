package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月结汇总：按日拆开，外加全月合计。
 *
 * <p>财务对账看的是趋势与异常日，所以「按日分组」比「一个总数」有用得多 ——
 * 一个月 30 万收入里某天突然多 5 万，只有按日看才看得出。</p>
 */
@Getter
@Builder
public class MonthlySettleVO {

    /** yyyy-MM */
    private final String month;

    private final BigDecimal totalAmount;

    private final BigDecimal refundAmount;

    private final long chargeCount;

    private final List<DailyRow> days;

    @Getter
    @Builder
    public static class DailyRow {
        private final String day;

        private final BigDecimal chargeAmount;

        private final BigDecimal refundAmount;

        private final long chargeCount;
    }
}
