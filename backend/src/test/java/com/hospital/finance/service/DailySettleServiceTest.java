package com.hospital.finance.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.dto.DailySettleVO;
import com.hospital.finance.dto.DailySubmitRequest;
import com.hospital.finance.entity.BizDailySettle;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizDailySettleMapper;
import com.hospital.finance.mapper.BizRefundMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日结月结：账实相符、差异必填说明、一天只留一条记录。
 *
 * <p>日结守的是「系统说的钱 = 抽屉里的钱」。差一块钱也要写清楚差在哪，
 * 放过去一次，「差个几十块正常」就成了默许的漏洞。</p>
 */
@ExtendWith(MockitoExtension.class)
class DailySettleServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);
    private static final Long OPERATOR_ID = 7L;

    @Mock
    private BizChargeMapper chargeMapper;
    @Mock
    private BizRefundMapper refundMapper;
    @Mock
    private BizDailySettleMapper dailySettleMapper;

    private DailySettleService service;

    @BeforeEach
    void setUp() {
        service = new DailySettleService(chargeMapper, refundMapper, dailySettleMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("fin")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    /** 现金 1000（2 笔）、微信 500（1 笔）、医保 300（1 笔） */
    private void stubIncome() {
        when(chargeMapper.sumByPayType(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new DailySettleService.PayRow(1, new BigDecimal("1000.00"), 2),
                        new DailySettleService.PayRow(2, new BigDecimal("500.00"), 1),
                        new DailySettleService.PayRow(4, new BigDecimal("300.00"), 1)));
    }

    @Test
    @DisplayName("对账口径：各支付方式之和 = 收费总额")
    void totalEqualsSumOfPayTypes() {
        stubIncome();
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(null);

        var vo = service.daily(DAY);

        assertThat(vo.getTotalAmount()).isEqualByComparingTo("1800.00");
        assertThat(vo.getChargeCount()).isEqualTo(4);
        BigDecimal sum = vo.getPayTypeAmounts().stream()
                .map(DailySettleVO.PayTypeAmount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(vo.getTotalAmount());
    }

    @Test
    @DisplayName("应有现金 = 现金收入 − 现金退费（微信退费不动抽屉）")
    void expectedCashExcludesNonCashRefund() {
        stubIncome();
        // 退费 200，其中只有 50 是现金原路退回
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(
                        new BigDecimal("200.00"), new BigDecimal("50.00"), 1));
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(null);

        var vo = service.daily(DAY);

        assertThat(vo.getExpectedCash()).isEqualByComparingTo("950.00");
        assertThat(vo.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(vo.getSubmitted()).isFalse();
    }

    @Test
    @DisplayName("已交班的日子回填实点与差异")
    void submittedDayShowsActualCash() {
        stubIncome();
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        BizDailySettle submitted = new BizDailySettle();
        submitted.setSettleDate(DAY);
        submitted.setActualCash(new BigDecimal("990.00"));
        submitted.setDiffAmount(new BigDecimal("-10.00"));
        submitted.setDiffRemark("找零出错");
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(submitted);

        var vo = service.daily(DAY);

        assertThat(vo.getSubmitted()).isTrue();
        assertThat(vo.getActualCash()).isEqualByComparingTo("990.00");
        assertThat(vo.getDiffAmount()).isEqualByComparingTo("-10.00");
        assertThat(vo.getDiffRemark()).isEqualTo("找零出错");
    }

    @Test
    @DisplayName("账实相符时可以不填说明")
    void submitWithNoDiff() {
        stubIncome();
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(null);
        loginAs(OPERATOR_ID, "finance");

        service.submit(request(new BigDecimal("1000.00"), null));

        ArgumentCaptor<BizDailySettle> captor = ArgumentCaptor.forClass(BizDailySettle.class);
        verify(dailySettleMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiffAmount()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(captor.getValue().getSubmitTime()).isNotNull();
    }

    @Test
    @DisplayName("差异非 0 未填说明 -> 拒绝，且不落库")
    void submitWithDiffRequiresRemark() {
        stubIncome();
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(null);
        loginAs(OPERATOR_ID, "finance");

        assertThatThrownBy(() -> service.submit(request(new BigDecimal("950.00"), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须填写差异说明");

        verify(dailySettleMapper, never()).insert(any(BizDailySettle.class));
    }

    @Test
    @DisplayName("差异非 0 但填了说明 -> 通过并记录差异")
    void submitWithDiffAndRemark() {
        stubIncome();
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(null);
        loginAs(OPERATOR_ID, "finance");

        service.submit(request(new BigDecimal("950.00"), "患者少付 50，已补齐"));

        ArgumentCaptor<BizDailySettle> captor = ArgumentCaptor.forClass(BizDailySettle.class);
        verify(dailySettleMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiffAmount()).isEqualByComparingTo("-50.00");
        assertThat(captor.getValue().getDiffRemark()).isEqualTo("患者少付 50，已补齐");
    }

    @Test
    @DisplayName("重复提交是覆盖，不是追加（一天只能有一条）")
    void repeatSubmitUpdatesExisting() {
        stubIncome();
        when(refundMapper.sumRefund(any(), any()))
                .thenReturn(new DailySettleService.RefundRow(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        BizDailySettle existing = new BizDailySettle();
        existing.setId(9001L);
        existing.setSettleDate(DAY);
        when(dailySettleMapper.selectByDate(DAY)).thenReturn(existing);
        loginAs(OPERATOR_ID, "finance");

        service.submit(request(new BigDecimal("1000.00"), null));

        verify(dailySettleMapper).updateById(any(BizDailySettle.class));
        verify(dailySettleMapper, never()).insert(any(BizDailySettle.class));
    }

    @Test
    @DisplayName("月结：按日升序，合计等于各日之和")
    void monthlyBreaksDownByDay() {
        YearMonth month = YearMonth.of(2026, 10);
        when(chargeMapper.sumByDay(any(), any())).thenReturn(List.of(
                new DailySettleService.DayRow("2026-10-02", new BigDecimal("300.00"), 2),
                new DailySettleService.DayRow("2026-10-01", new BigDecimal("100.00"), 1)));
        when(refundMapper.sumRefundByDay(any(), any())).thenReturn(List.of(
                new DailySettleService.DayRow("2026-10-02", new BigDecimal("30.00"), 1)));

        var vo = service.monthly(month);

        // TreeMap 保证按日升序，前端不用再排
        assertThat(vo.getDays()).extracting("day").containsExactly("2026-10-01", "2026-10-02");
        assertThat(vo.getTotalAmount()).isEqualByComparingTo("400.00");
        assertThat(vo.getRefundAmount()).isEqualByComparingTo("30.00");
        assertThat(vo.getChargeCount()).isEqualTo(3);
        assertThat(vo.getMonth()).isEqualTo("2026-10");
    }

    @Test
    @DisplayName("没有任何流水时月结返回空列表而不是报错")
    void monthlyWithNoData() {
        when(chargeMapper.sumByDay(any(), any())).thenReturn(List.of());
        when(refundMapper.sumRefundByDay(any(), any())).thenReturn(List.of());

        var vo = service.monthly(YearMonth.of(2026, 10));

        assertThat(vo.getDays()).isEmpty();
        assertThat(vo.getTotalAmount()).isEqualByComparingTo("0");
    }

    private DailySubmitRequest request(BigDecimal actualCash, String remark) {
        DailySubmitRequest request = new DailySubmitRequest();
        request.setSettleDate(DAY);
        request.setActualCash(actualCash);
        request.setDiffRemark(remark);
        return request;
    }
}
