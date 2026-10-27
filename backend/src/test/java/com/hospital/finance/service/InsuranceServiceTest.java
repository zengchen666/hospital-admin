package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.dto.InsuranceSettleRequest;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizChargeItem;
import com.hospital.finance.entity.BizInsuranceSettle;
import com.hospital.finance.mapper.BizChargeItemMapper;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizInsuranceSettleMapper;
import com.hospital.patient.mapper.BizPatientMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 医保结算（一期 Mock）：按明细类别算、封顶、不能重复上送、报文留档。
 */
@ExtendWith(MockitoExtension.class)
class InsuranceServiceTest {

    private static final Long CHARGE_ID = 4001L;
    private static final Long SETTLE_ID = 6001L;

    @Mock
    private BizInsuranceSettleMapper settleMapper;
    @Mock
    private BizChargeMapper chargeMapper;
    @Mock
    private BizChargeItemMapper chargeItemMapper;
    @Mock
    private BizPatientMapper patientMapper;

    private InsuranceService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizChargeItem.class);
        TableInfoHelper.initTableInfo(assistant, BizCharge.class);
    }

    @BeforeEach
    void setUp() {
        service = new InsuranceService(settleMapper, chargeMapper, chargeItemMapper, patientMapper);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizInsuranceSettle entity = inv.getArgument(0);
            entity.setId(SETTLE_ID);
            return 1;
        }).when(settleMapper).insert(any(BizInsuranceSettle.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("cashier")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizCharge charge(Integer payStatus, BigDecimal actual) {
        BizCharge entity = new BizCharge();
        entity.setId(CHARGE_ID);
        entity.setChargeNo("CHG202610270001");
        entity.setPatientId(100L);
        entity.setActualAmount(actual);
        entity.setPayStatus(payStatus);
        return entity;
    }

    /** itemType：1 药品（甲类 90%）/ 2 诊疗（乙类 70%）/ 3 检查（乙类 70%） */
    private BizChargeItem item(int itemType, BigDecimal amount) {
        BizChargeItem entity = new BizChargeItem();
        entity.setId(5001L);
        entity.setChargeId(CHARGE_ID);
        entity.setItemType(itemType);
        entity.setItemName("项目");
        entity.setAmount(amount);
        return entity;
    }

    private InsuranceSettleRequest request() {
        InsuranceSettleRequest request = new InsuranceSettleRequest();
        request.setChargeId(CHARGE_ID);
        request.setInsuranceType("职工");
        return request;
    }

    @Test
    @DisplayName("未支付的收费单不能结算")
    void unpaidChargeCannotSettle() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(0, new BigDecimal("100.00")));
        loginAs(5L, "cashier");

        assertThatThrownBy(() -> service.settle(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未支付");

        verify(settleMapper, never()).insert(any(BizInsuranceSettle.class));
    }

    @Test
    @DisplayName("统筹 + 自付 = 总费用（甲类 90%、乙类 70% 逐项算）")
    void amountsAddUp() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("300.00")));
        when(settleMapper.selectLatestByCharge(CHARGE_ID)).thenReturn(null);
        // 药品 100 × 90% = 90；诊疗 200 × 70% = 140 → 统筹 230，自付 70
        when(chargeItemMapper.selectList(any())).thenReturn(List.of(
                item(1, new BigDecimal("100.00")),
                item(2, new BigDecimal("200.00"))));
        loginAs(5L, "cashier");

        service.settle(request());

        ArgumentCaptor<BizInsuranceSettle> captor = ArgumentCaptor.forClass(BizInsuranceSettle.class);
        verify(settleMapper).insert(captor.capture());
        BizInsuranceSettle saved = captor.getValue();
        assertThat(saved.getInsuranceAmount()).isEqualByComparingTo("230.00");
        assertThat(saved.getSelfAmount()).isEqualByComparingTo("70.00");
        assertThat(saved.getInsuranceAmount().add(saved.getSelfAmount()))
                .isEqualByComparingTo(saved.getTotalAmount());
    }

    @Test
    @DisplayName("统筹金额不超过封顶线，也不超过总额")
    void cappedAndClamped() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("100.00")));
        when(settleMapper.selectLatestByCharge(CHARGE_ID)).thenReturn(null);
        // 明细金额 9000 × 90% = 8100，但总额只有 100 → 只能报 100
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(item(1, new BigDecimal("9000.00"))));
        loginAs(5L, "cashier");

        service.settle(request());

        ArgumentCaptor<BizInsuranceSettle> captor = ArgumentCaptor.forClass(BizInsuranceSettle.class);
        verify(settleMapper).insert(captor.capture());
        assertThat(captor.getValue().getInsuranceAmount()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().getSelfAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("已结算成功的单不能重复上送（真接口会判重复交易）")
    void duplicateSettleIsRejected() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("100.00")));
        BizInsuranceSettle settled = new BizInsuranceSettle();
        settled.setId(SETTLE_ID);
        settled.setChargeId(CHARGE_ID);
        settled.setSettleStatus(1);
        settled.setSettleNo("YB001");
        when(settleMapper.selectLatestByCharge(CHARGE_ID)).thenReturn(settled);
        loginAs(5L, "cashier");

        assertThatThrownBy(() -> service.settle(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结算成功");

        verify(settleMapper, never()).insert(any(BizInsuranceSettle.class));
    }

    @Test
    @DisplayName("上送与返回报文原样落库，便于事后排查")
    void requestAndResponseArePersisted() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("100.00")));
        when(settleMapper.selectLatestByCharge(CHARGE_ID)).thenReturn(null);
        when(chargeItemMapper.selectList(any()))
                .thenReturn(List.of(item(1, new BigDecimal("100.00"))));
        loginAs(5L, "cashier");

        service.settle(request());

        ArgumentCaptor<BizInsuranceSettle> captor = ArgumentCaptor.forClass(BizInsuranceSettle.class);
        verify(settleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRequestBody()).contains("CHG202610270001");
        assertThat(captor.getValue().getResponseBody()).contains("resultCode");
        assertThat(captor.getValue().getSettleTime()).isNotNull();
    }

    @Test
    @DisplayName("没有明细时不虚报统筹金额")
    void noItemsMeansNoInsuranceAmount() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("100.00")));
        when(settleMapper.selectLatestByCharge(CHARGE_ID)).thenReturn(null);
        when(chargeItemMapper.selectList(any())).thenReturn(List.of());
        loginAs(5L, "cashier");

        service.settle(request());

        ArgumentCaptor<BizInsuranceSettle> captor = ArgumentCaptor.forClass(BizInsuranceSettle.class);
        verify(settleMapper).insert(captor.capture());
        assertThat(captor.getValue().getInsuranceAmount()).isEqualByComparingTo("0.00");
        assertThat(captor.getValue().getSelfAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("没有结算记录时查详情直接 404")
    void missingSettleRecord() {
        when(settleMapper.selectLatestByCharge(CHARGE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detailByCharge(CHARGE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚无医保结算记录");
    }
}
