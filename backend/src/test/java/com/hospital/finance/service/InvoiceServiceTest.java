package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.entity.BizCharge;
import com.hospital.finance.entity.BizInvoice;
import com.hospital.finance.mapper.BizChargeMapper;
import com.hospital.finance.mapper.BizInvoiceMapper;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.system.mapper.SysUserMapper;
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
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发票：金额取实收快照、一单一票、作废不删记录。
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final Long CHARGE_ID = 4001L;
    private static final Long INVOICE_ID = 5001L;

    @Mock
    private BizInvoiceMapper invoiceMapper;
    @Mock
    private BizChargeMapper chargeMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private InvoiceNoGenerator noGenerator;

    private InvoiceService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizInvoice.class);
        TableInfoHelper.initTableInfo(assistant, BizCharge.class);
    }

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceMapper, chargeMapper, patientMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizInvoice entity = inv.getArgument(0);
            entity.setId(INVOICE_ID);
            return 1;
        }).when(invoiceMapper).insert(any(BizInvoice.class));
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
        entity.setChargeTime(LocalDateTime.now());
        return entity;
    }

    private BizInvoice invoice(Integer status) {
        BizInvoice entity = new BizInvoice();
        entity.setId(INVOICE_ID);
        entity.setInvoiceNo("FP202610270001");
        entity.setChargeId(CHARGE_ID);
        entity.setInvoiceType(1);
        entity.setAmount(new BigDecimal("30.00"));
        entity.setStatus(status);
        entity.setPrintTime(LocalDateTime.now());
        return entity;
    }

    @Test
    @DisplayName("未支付的收费单不能开票")
    void unpaidChargeCannotBeInvoiced() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(0, new BigDecimal("30.00")));
        loginAs(5L, "cashier");

        assertThatThrownBy(() -> service.print(CHARGE_ID, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未支付");

        verify(invoiceMapper, never()).insert(any(BizInvoice.class));
    }

    @Test
    @DisplayName("金额取收费单实收快照，不跟着调价变")
    void amountIsSnapshotOfActualAmount() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("67.50")));
        when(invoiceMapper.selectValidByCharge(CHARGE_ID)).thenReturn(null);
        when(noGenerator.next()).thenReturn("FP202610270001");
        loginAs(5L, "cashier");

        service.print(CHARGE_ID, 1);

        ArgumentCaptor<BizInvoice> captor = ArgumentCaptor.forClass(BizInvoice.class);
        verify(invoiceMapper).insert(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("67.50");
        assertThat(captor.getValue().getInvoiceType()).isEqualTo(1);
        assertThat(captor.getValue().getPrintTime()).isNotNull();
    }

    @Test
    @DisplayName("一张收费单只能有一张有效发票")
    void duplicateInvoiceIsRejected() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("30.00")));
        when(invoiceMapper.selectValidByCharge(CHARGE_ID)).thenReturn(invoice(0));
        loginAs(5L, "cashier");

        assertThatThrownBy(() -> service.print(CHARGE_ID, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已开过发票");

        verify(invoiceMapper, never()).insert(any(BizInvoice.class));
    }

    @Test
    @DisplayName("作废后可以对同一张收费单重新开票")
    void canReissueAfterVoid() {
        when(chargeMapper.selectById(CHARGE_ID)).thenReturn(charge(1, new BigDecimal("30.00")));
        when(invoiceMapper.selectValidByCharge(CHARGE_ID)).thenReturn(null);
        when(noGenerator.next()).thenReturn("FP202610270002");
        loginAs(5L, "cashier");

        service.print(CHARGE_ID, 2);

        ArgumentCaptor<BizInvoice> captor = ArgumentCaptor.forClass(BizInvoice.class);
        verify(invoiceMapper).insert(captor.capture());
        assertThat(captor.getValue().getInvoiceType()).isEqualTo(2);
    }

    @Test
    @DisplayName("作废是改状态，不做物理删除")
    void voidKeepsRecord() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(0));
        when(invoiceMapper.voidInvoice(INVOICE_ID)).thenReturn(1);
        loginAs(7L, "finance");

        service.voidInvoice(INVOICE_ID);

        verify(invoiceMapper).voidInvoice(INVOICE_ID);
        // 发票号已报税，物理删除会让「这张票去哪了」永远查不到
        verify(invoiceMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("重复作废返回冲突，不产生第二条作废流水")
    void duplicateVoidIsRejected() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(0));
        when(invoiceMapper.voidInvoice(INVOICE_ID)).thenReturn(0);
        loginAs(7L, "finance");

        assertThatThrownBy(() -> service.voidInvoice(INVOICE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("详情：装配收费单号与患者名")
    void detailAssemblesChargeInfo() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(0));
        when(chargeMapper.selectByIds(any())).thenReturn(java.util.List.of(
                charge(1, new BigDecimal("30.00"))));
        com.hospital.patient.entity.BizPatient patient = new com.hospital.patient.entity.BizPatient();
        patient.setId(100L);
        patient.setName("张三");
        when(patientMapper.selectByIds(any())).thenReturn(java.util.List.of(patient));

        var vo = service.detail(INVOICE_ID);

        assertThat(vo.getChargeNo()).isEqualTo("CHG202610270001");
        assertThat(vo.getPatientName()).isEqualTo("张三");
        assertThat(vo.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("不存在的发票直接 404")
    void missingInvoice() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(INVOICE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发票不存在");
    }

    @Test
    @DisplayName("按收费单号查：先反查收费单再收窄，查不到返回空页而不是全表")
    void searchByChargeNoReturnsEmptyWhenNoMatch() {
        when(chargeMapper.selectList(any())).thenReturn(java.util.List.of());

        var query = new com.hospital.finance.dto.InvoiceQuery();
        query.setChargeNo("NOT-EXIST");
        var result = service.page(query);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(invoiceMapper, never()).selectPage(any(), any());
    }
}
