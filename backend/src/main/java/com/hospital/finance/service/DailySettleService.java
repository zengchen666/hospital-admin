package com.hospital.finance.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.finance.dto.DailySubmitRequest;
import com.hospital.finance.dto.DailySettleVO;
import com.hospital.finance.dto.MonthlySettleVO;
import com.hospital.finance.entity.BizDailySettle;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizDailySettleMapper;
import com.hospital.finance.mapper.BizRefundMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日结 / 月结。
 *
 * <p><b>日结的核心是「账实相符」</b>：系统说今天收了 1200 现金，
 * 抽屉里就得有 1200。差一块钱也要写清楚差在哪，
 * 否则长此以往「差个几十块正常」就成了默许的漏洞。</p>
 *
 * <p><b>收入按支付方式分开统计，但只对现金</b>：微信支付宝的钱本来就在账户里，
 * 不需要收费员交；只有现金进了抽屉，才存在「实点」这回事。</p>
 */
@Slf4j
@Service
public class DailySettleService {

    /** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
    private static final int PAY_CASH = 1;
    private static final int[] PAY_TYPES = {1, 2, 3, 4, 5};

    private final BizChargeMapper chargeMapper;
    private final BizRefundMapper refundMapper;
    private final BizDailySettleMapper dailySettleMapper;

    public DailySettleService(BizChargeMapper chargeMapper,
                              BizRefundMapper refundMapper,
                              BizDailySettleMapper dailySettleMapper) {
        this.chargeMapper = chargeMapper;
        this.refundMapper = refundMapper;
        this.dailySettleMapper = dailySettleMapper;
    }

    /**
     * 日结汇总。
     *
     * <p>已提交过的日子回填实点现金与差异，让收费员能看到自己交过班；
     * 但<b>汇总数字永远是实时算的</b>，不读日结表的快照 ——
     * 万一交班后又有人补了一笔收费，看快照会以为账还是平的。</p>
     */
    @Transactional(readOnly = true)
    public DailySettleVO daily(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();

        List<PayRow> payRows = chargeMapper.sumByPayType(from, to);
        Map<Integer, PayRow> byType = payRows.stream()
                .collect(Collectors.toMap(PayRow::payType, Function.identity()));

        long chargeCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<DailySettleVO.PayTypeAmount> amounts = new ArrayList<>(PAY_TYPES.length);
        for (int type : PAY_TYPES) {
            PayRow row = byType.get(type);
            long count = row == null ? 0L : row.count();
            BigDecimal amount = row == null ? BigDecimal.ZERO : nvl(row.amount());
            chargeCount += count;
            total = total.add(amount);
            amounts.add(DailySettleVO.PayTypeAmount.builder()
                    .payType(type).amount(amount).count(count).build());
        }

        RefundRow refund = refundMapper.sumRefund(from, to);
        long refundCount = refund == null ? 0L : nvlLong(refund.count());
        BigDecimal refundAmount = refund == null ? BigDecimal.ZERO : nvl(refund.amount());
        // 现金退费也要从抽屉里出去，所以应有现金 = 现金收入 − 现金退费
        BigDecimal cashRefund = refund == null ? BigDecimal.ZERO : nvl(refund.cashAmount());
        BigDecimal cashIncome = byType.get(PAY_CASH) == null
                ? BigDecimal.ZERO : nvl(byType.get(PAY_CASH).amount());
        BigDecimal expectedCash = cashIncome.subtract(cashRefund);

        BizDailySettle submitted = dailySettleMapper.selectByDate(day);
        return DailySettleVO.builder()
                .settleDate(day)
                .chargeCount(chargeCount)
                .totalAmount(total)
                .payTypeAmounts(amounts)
                .refundCount(refundCount)
                .refundAmount(refundAmount)
                .expectedCash(expectedCash)
                .actualCash(submitted == null ? null : submitted.getActualCash())
                .diffAmount(submitted == null ? null : submitted.getDiffAmount())
                .diffRemark(submitted == null ? null : submitted.getDiffRemark())
                .submitted(submitted != null)
                .build();
    }

    /**
     * 提交日结。
     *
     * <p><b>差异非 0 必须填说明</b>：这是整条链路里唯一能拦住
     * 「随便填个数交差」的地方。说明会进审计日志，第二天查的就是它。</p>
     *
     * <p>一天只留一条记录（唯一键 settle_date）：重复提交是覆盖，
     * 同一个人交两次班产生两条日结，账就对不上了。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(DailySubmitRequest request) {
        DailySettleVO summary = daily(request.getSettleDate());
        BigDecimal diff = request.getActualCash().subtract(nvl(summary.getExpectedCash()));
        if (diff.compareTo(BigDecimal.ZERO) != 0 && !hasText(request.getDiffRemark())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "实点现金与应有现金存在差异（" + diff + " 元），必须填写差异说明");
        }

        Long operatorId = CurrentUser.requireId();
        BizDailySettle existing = dailySettleMapper.selectByDate(request.getSettleDate());
        BizDailySettle entity = existing == null ? new BizDailySettle() : existing;
        entity.setSettleDate(request.getSettleDate());
        entity.setChargeCount(summary.getChargeCount());
        entity.setTotalAmount(summary.getTotalAmount());
        entity.setRefundCount(summary.getRefundCount());
        entity.setRefundAmount(summary.getRefundAmount());
        entity.setExpectedCash(summary.getExpectedCash());
        entity.setActualCash(request.getActualCash());
        entity.setDiffAmount(diff);
        entity.setDiffRemark(request.getDiffRemark());
        entity.setOperatorId(operatorId);
        entity.setSubmitTime(LocalDateTime.now());

        if (existing == null) {
            dailySettleMapper.insert(entity);
        } else {
            dailySettleMapper.updateById(entity);
        }
        log.info("提交日结 date={} 应有={} 实点={} 差异={} operator={}",
                request.getSettleDate(), summary.getExpectedCash(), request.getActualCash(),
                diff, operatorId);
    }

    /**
     * 月结：按日拆开 + 全月合计。
     *
     * <p>财务看的是趋势与异常日，一个总数没有意义 ——
     * 一个月 30 万里某天突然多 5 万，只有按日看才看得出。</p>
     */
    @Transactional(readOnly = true)
    public MonthlySettleVO monthly(YearMonth month) {
        YearMonth target = month == null ? YearMonth.now() : month;
        LocalDate from = target.atDay(1);
        LocalDate to = target.plusMonths(1).atDay(1);

        List<DayRow> chargeRows = chargeMapper.sumByDay(from.atStartOfDay(), to.atStartOfDay());
        List<DayRow> refundRows = refundMapper.sumRefundByDay(from.atStartOfDay(), to.atStartOfDay());
        Map<String, BigDecimal> refundByDay = refundRows.stream()
                .collect(Collectors.toMap(DayRow::day, r -> nvl(r.amount()), BigDecimal::add));

        // TreeMap 保证按日升序，前端不用再排一遍
        Map<String, DailyRowBuilder> builders = new TreeMap<>();
        for (DayRow row : chargeRows) {
            builders.computeIfAbsent(row.day(), k -> new DailyRowBuilder())
                    .chargeAmount = nvl(row.amount());
            builders.get(row.day()).chargeCount = row.count();
        }
        for (Map.Entry<String, BigDecimal> e : refundByDay.entrySet()) {
            builders.computeIfAbsent(e.getKey(), k -> new DailyRowBuilder())
                    .refundAmount = e.getValue();
        }

        List<MonthlySettleVO.DailyRow> days = new ArrayList<>(builders.size());
        BigDecimal monthTotal = BigDecimal.ZERO;
        BigDecimal monthRefund = BigDecimal.ZERO;
        long monthCount = 0;
        for (Map.Entry<String, DailyRowBuilder> e : builders.entrySet()) {
            DailyRowBuilder b = e.getValue();
            days.add(MonthlySettleVO.DailyRow.builder()
                    .day(e.getKey())
                    .chargeAmount(b.chargeAmount)
                    .refundAmount(b.refundAmount)
                    .chargeCount(b.chargeCount)
                    .build());
            monthTotal = monthTotal.add(b.chargeAmount);
            monthRefund = monthRefund.add(b.refundAmount);
            monthCount += b.chargeCount;
        }

        return MonthlySettleVO.builder()
                .month(target.toString())
                .totalAmount(monthTotal)
                .refundAmount(monthRefund)
                .chargeCount(monthCount)
                .days(days)
                .build();
    }

    private static final class DailyRowBuilder {
        private BigDecimal chargeAmount = BigDecimal.ZERO;
        private BigDecimal refundAmount = BigDecimal.ZERO;
        private long chargeCount;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long nvlLong(Long value) {
        return value == null ? 0L : value;
    }

    /** 支付分项统计行 */
    public record PayRow(Integer payType, BigDecimal amount, long count) {
    }

    /** 退费统计行 */
    public record RefundRow(BigDecimal amount, BigDecimal cashAmount, long count) {
    }

    /** 按日统计行 */
    public record DayRow(String day, BigDecimal amount, long count) {
    }
}
