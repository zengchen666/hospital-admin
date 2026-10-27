package com.hospital.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 日结汇总。
 *
 * <p><b>对账口径</b>：{@code totalAmount} = 各支付方式金额之和。
 * 这个等式必须由服务端保证，前端只展示 ——
 * 让前端自己加一遍，等于对账结果可以被改。</p>
 *
 * <p>{@code expectedCash} 只统计现金（含银行卡外的实点部分）：
 * 收费员下班要交的是现金，微信支付宝的钱本来就在账户里，不进抽屉。</p>
 */
@Getter
@Builder
public class DailySettleVO {

    private final LocalDate settleDate;

    /** 收费笔数 */
    private final long chargeCount;

    private final BigDecimal totalAmount;

    /** 各支付方式分项，前端直接按 1~5 渲染 */
    private final List<PayTypeAmount> payTypeAmounts;

    /** 退费笔数 / 退费金额（冲减当日收入） */
    private final long refundCount;

    private final BigDecimal refundAmount;

    /** 应有现金 = 现金收入 − 现金退费 */
    private final BigDecimal expectedCash;

    /** 已提交日结时回填；未提交为 null */
    private final BigDecimal actualCash;

    private final BigDecimal diffAmount;

    private final String diffRemark;

    private final Boolean submitted;

    @Getter
    @Builder
    public static class PayTypeAmount {
        /** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
        private final Integer payType;

        private final BigDecimal amount;

        private final long count;
    }
}
