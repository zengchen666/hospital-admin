package com.hospital.pharmacy.service;

import com.hospital.clinic.entity.BizPrescription;
import com.hospital.clinic.entity.BizPrescriptionItem;
import com.hospital.clinic.mapper.BizPrescriptionItemMapper;
import com.hospital.clinic.mapper.BizPrescriptionMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.pharmacy.dto.DispenseRequest;
import com.hospital.pharmacy.dto.DispenseResult;
import com.hospital.pharmacy.entity.BizDispense;
import com.hospital.pharmacy.entity.BizDispenseItem;
import com.hospital.pharmacy.entity.BizStock;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.entity.BizWarehouse;
import com.hospital.pharmacy.mapper.BizDispenseItemMapper;
import com.hospital.pharmacy.mapper.BizDispenseMapper;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
import com.hospital.pharmacy.mapper.BizWarehouseMapper;
import com.hospital.system.mapper.SysUserMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门诊发药：按批号扣减、近效期优先、库存不足整单回滚、账实一致。
 *
 * <p>发药是库存真正减少的唯一入口，也是最容易出现「账实不符」的环节，
 * 所以这里逐条断言出库批号与数量，而不只看接口是否返回 200。</p>
 */
@ExtendWith(MockitoExtension.class)
class DispenseServiceTest {

    private static final Long PRESC_ID = 9001L;
    private static final Long PATIENT_ID = 100L;
    private static final Long WAREHOUSE_ID = 8002L;
    private static final Long DRUG_ID = 9101L;
    private static final Long OPERATOR_ID = 1L;

    @Mock
    private BizDispenseMapper dispenseMapper;
    @Mock
    private BizDispenseItemMapper dispenseItemMapper;
    @Mock
    private BizStockMapper stockMapper;
    @Mock
    private BizStockRecordMapper stockRecordMapper;
    @Mock
    private BizWarehouseMapper warehouseMapper;
    @Mock
    private BizPrescriptionMapper prescriptionMapper;
    @Mock
    private BizPrescriptionItemMapper prescriptionItemMapper;
    @Mock
    private BizDrugMapper drugMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private DispenseNoGenerator noGenerator;

    private DispenseService service;

    @BeforeEach
    void setUp() {
        service = new DispenseService(dispenseMapper, dispenseItemMapper, stockMapper,
                stockRecordMapper, warehouseMapper, prescriptionMapper, prescriptionItemMapper,
                drugMapper, patientMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        // lenient：前置校验失败的用例走不到 insert
        lenient().doAnswer(inv -> {
            BizDispense entity = inv.getArgument(0);
            entity.setId(6001L);
            return 1;
        }).when(dispenseMapper).insert(any(BizDispense.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("pharmacist")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPrescription prescription(int status) {
        BizPrescription entity = new BizPrescription();
        entity.setId(PRESC_ID);
        entity.setPrescNo("PRESC202610260001");
        entity.setPatientId(PATIENT_ID);
        entity.setDoctorId(3L);
        entity.setStatus(status);
        entity.setTotalAmount(new BigDecimal("20.00"));
        return entity;
    }

    private BizPrescriptionItem prescItem(int quantity, String price) {
        BizPrescriptionItem entity = new BizPrescriptionItem();
        entity.setDrugId(DRUG_ID);
        entity.setDrugName("氯化钠注射液");
        entity.setQuantity(quantity);
        entity.setPrice(new BigDecimal(price));
        return entity;
    }

    private BizWarehouse warehouse(int status) {
        BizWarehouse entity = new BizWarehouse();
        entity.setId(WAREHOUSE_ID);
        entity.setWarehouseName("门诊西药房");
        entity.setStatus(status);
        return entity;
    }

    private BizStock batch(Long id, String batchNo, int quantity, LocalDate expire) {
        BizStock stock = new BizStock();
        stock.setId(id);
        stock.setDrugId(DRUG_ID);
        stock.setWarehouseId(WAREHOUSE_ID);
        stock.setBatchNo(batchNo);
        stock.setQuantity(quantity);
        stock.setExpireDate(expire);
        return stock;
    }

    private DispenseRequest request() {
        DispenseRequest request = new DispenseRequest();
        request.setPrescId(PRESC_ID);
        request.setWarehouseId(WAREHOUSE_ID);
        return request;
    }

    @Test
    @DisplayName("只有已收费的处方才能发药")
    void onlyChargedPrescriptionCanBeDispensed() {
        loginAs(OPERATOR_ID, "pharm");

        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(1));
        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("尚未收费");

        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(3));
        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("已发药");

        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(4));
        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("已作废");

        verify(dispenseMapper, never()).insert(any(BizDispense.class));
    }

    @Test
    @DisplayName("库房不存在或已停用时拒绝发药")
    void invalidWarehouseIsRejected() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        loginAs(OPERATOR_ID, "pharm");

        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("库房");

        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(0));
        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("库房");

        verify(dispenseMapper, never()).insert(any(BizDispense.class));
    }

    @Test
    @DisplayName("处方无明细时拒绝发药")
    void emptyPrescriptionIsRejected() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(1));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of());
        loginAs(OPERATOR_ID, "pharm");

        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("没有药品明细");
    }

    @Test
    @DisplayName("一个药品跨多个批次时按近效期顺序拆分出库")
    void singleDrugSplitsAcrossBatches() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(1));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(prescItem(5, "4.00")));
        // 近效期批次只有 3 盒，需要再从下一批出 2 盒
        when(stockMapper.selectAvailable(DRUG_ID, WAREHOUSE_ID)).thenReturn(List.of(
                batch(1L, "B-NEAR", 3, LocalDate.now().plusMonths(2)),
                batch(2L, "B-FAR", 10, LocalDate.now().plusYears(2))));
        when(stockMapper.decrease(1L, 3)).thenReturn(1);
        when(stockMapper.decrease(2L, 2)).thenReturn(1);
        when(noGenerator.next()).thenReturn("DSP202610260001");
        when(dispenseMapper.transitStatus(6001L, 0, 1, OPERATOR_ID)).thenReturn(1);
        when(prescriptionMapper.transitStatus(PRESC_ID, 2, 3, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "pharm");

        DispenseResult result = service.dispense(request());

        assertThat(result.getItemCount()).isEqualTo(2);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));

        ArgumentCaptor<BizDispenseItem> itemCaptor = ArgumentCaptor.forClass(BizDispenseItem.class);
        verify(dispenseItemMapper, times(2)).insert(itemCaptor.capture());
        List<BizDispenseItem> items = itemCaptor.getAllValues();
        assertThat(items.get(0).getBatchNo()).isEqualTo("B-NEAR");
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
        assertThat(items.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(items.get(1).getBatchNo()).isEqualTo("B-FAR");
        assertThat(items.get(1).getQuantity()).isEqualTo(2);
        assertThat(items.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("8.00"));

        // 每次扣减都必须留流水
        ArgumentCaptor<BizStockRecord> recordCaptor = ArgumentCaptor.forClass(BizStockRecord.class);
        verify(stockRecordMapper, times(2)).insert(recordCaptor.capture());
        assertThat(recordCaptor.getAllValues()).allSatisfy(record -> {
            assertThat(record.getRecordType()).isEqualTo(2);
            assertThat(record.getRelatedNo()).isEqualTo("DSP202610260001");
        });
    }

    @Test
    @DisplayName("库存不足时整单回滚，不会只发一半")
    void insufficientStockRollsBackWholeDispense() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(1));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(prescItem(5, "4.00")));
        when(stockMapper.selectAvailable(DRUG_ID, WAREHOUSE_ID)).thenReturn(List.of(
                batch(1L, "B-ONLY", 3, LocalDate.now().plusMonths(1))));
        when(stockMapper.decrease(1L, 3)).thenReturn(1);
        when(noGenerator.next()).thenReturn("DSP202610260002");
        loginAs(OPERATOR_ID, "pharm");

        assertThatThrownBy(() -> service.dispense(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足")
                .hasMessageContaining("还差 2");

        // 事务回滚由 @Transactional 保证，这里确认没有推进处方状态
        verify(prescriptionMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("批次被并发抢走时自动尝试下一批，不直接失败")
    void concurrentBatchShortageFallsThroughToNextBatch() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(1));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(prescItem(5, "4.00")));
        when(stockMapper.selectAvailable(DRUG_ID, WAREHOUSE_ID)).thenReturn(List.of(
                batch(1L, "B-STOLEN", 5, LocalDate.now().plusMonths(1)),
                batch(2L, "B-SAFE", 20, LocalDate.now().plusYears(1))));
        // 第一批被别人抢走，条件更新影响 0 行
        when(stockMapper.decrease(1L, 5)).thenReturn(0);
        when(stockMapper.decrease(2L, 5)).thenReturn(1);
        when(noGenerator.next()).thenReturn("DSP202610260003");
        when(dispenseMapper.transitStatus(6001L, 0, 1, OPERATOR_ID)).thenReturn(1);
        when(prescriptionMapper.transitStatus(PRESC_ID, 2, 3, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "pharm");

        DispenseResult result = service.dispense(request());

        assertThat(result.getItemCount()).isEqualTo(1);
        ArgumentCaptor<BizDispenseItem> captor = ArgumentCaptor.forClass(BizDispenseItem.class);
        verify(dispenseItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getBatchNo()).isEqualTo("B-SAFE");
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("药房该药品完全没有库存时给出明确提示")
    void noStockAtAllIsRejected() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(1));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(prescItem(2, "4.00")));
        when(stockMapper.selectAvailable(DRUG_ID, WAREHOUSE_ID)).thenReturn(List.of());
        when(noGenerator.next()).thenReturn("DSP202610260004");
        loginAs(OPERATOR_ID, "pharm");

        assertThatThrownBy(() -> service.dispense(request()))
                .hasMessageContaining("已无库存");
    }

    @Test
    @DisplayName("发药成功后处方推进到已发药")
    void prescriptionIsMarkedDispensed() {
        when(prescriptionMapper.selectById(PRESC_ID)).thenReturn(prescription(2));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(1));
        when(prescriptionItemMapper.selectList(any())).thenReturn(List.of(prescItem(1, "4.00")));
        when(stockMapper.selectAvailable(DRUG_ID, WAREHOUSE_ID)).thenReturn(List.of(
                batch(1L, "B-1", 100, LocalDate.now().plusYears(1))));
        when(stockMapper.decrease(eq(1L), anyInt())).thenReturn(1);
        when(noGenerator.next()).thenReturn("DSP202610260005");
        when(dispenseMapper.transitStatus(6001L, 0, 1, OPERATOR_ID)).thenReturn(1);
        when(prescriptionMapper.transitStatus(PRESC_ID, 2, 3, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "pharm");

        service.dispense(request());

        verify(prescriptionMapper).transitStatus(PRESC_ID, 2, 3, OPERATOR_ID);
    }
}
