package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.pharmacy.dto.PurchaseInRequest;
import com.hospital.pharmacy.dto.PurchaseItemRequest;
import com.hospital.pharmacy.dto.PurchaseRequest;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.entity.BizPurchase;
import com.hospital.pharmacy.entity.BizPurchaseItem;
import com.hospital.pharmacy.entity.BizStock;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.entity.BizSupplier;
import com.hospital.pharmacy.entity.BizWarehouse;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.pharmacy.mapper.BizPurchaseItemMapper;
import com.hospital.pharmacy.mapper.BizPurchaseMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
import com.hospital.pharmacy.mapper.BizSupplierMapper;
import com.hospital.pharmacy.mapper.BizWarehouseMapper;
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
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购：建单 → 审核 → 入库。
 *
 * <p>本类守住三条内控红线：</p>
 * <ol>
 *   <li><b>审核不加库存</b> —— 审核是「同意买」，入库是货到验收后的独立动作；</li>
 *   <li><b>提单人不能审自己的单</b> —— 不相容职务；</li>
 *   <li><b>实到数量 0 或超采购量 一律拒绝</b> —— 单据要能反映真实的到货情况。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    private static final Long PURCHASE_ID = 7001L;
    private static final Long ITEM_ID = 8001L;
    private static final Long DRUG_ID = 9101L;
    private static final Long SUPPLIER_ID = 6001L;
    private static final Long WAREHOUSE_ID = 8001L;
    private static final Long APPLICANT_ID = 7L;
    private static final Long AUDITOR_ID = 9L;

    @Mock
    private BizPurchaseMapper purchaseMapper;
    @Mock
    private BizPurchaseItemMapper itemMapper;
    @Mock
    private BizSupplierMapper supplierMapper;
    @Mock
    private BizWarehouseMapper warehouseMapper;
    @Mock
    private BizDrugMapper drugMapper;
    @Mock
    private BizStockMapper stockMapper;
    @Mock
    private BizStockRecordMapper recordMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PurchaseNoGenerator noGenerator;

    private PurchaseService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizPurchase.class);
        TableInfoHelper.initTableInfo(assistant, BizPurchaseItem.class);
        TableInfoHelper.initTableInfo(assistant, BizStock.class);
    }

    @BeforeEach
    void setUp() {
        service = new PurchaseService(purchaseMapper, itemMapper, supplierMapper, warehouseMapper,
                drugMapper, stockMapper, recordMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizPurchase entity = inv.getArgument(0);
            entity.setId(PURCHASE_ID);
            return 1;
        }).when(purchaseMapper).insert(any(BizPurchase.class));
        lenient().doAnswer(inv -> {
            BizPurchaseItem entity = inv.getArgument(0);
            entity.setId(ITEM_ID);
            return 1;
        }).when(itemMapper).insert(any(BizPurchaseItem.class));
        lenient().when(noGenerator.next()).thenReturn("CG202610270001");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("u" + userId)
                .roleCodes(new LinkedHashSet<>(Set.of("pharm")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizSupplier supplier(Integer status) {
        BizSupplier supplier = new BizSupplier();
        supplier.setId(SUPPLIER_ID);
        supplier.setSupplierName("华东医药");
        supplier.setStatus(status);
        return supplier;
    }

    private BizPurchase purchase(Integer status, Long applicantId) {
        BizPurchase purchase = new BizPurchase();
        purchase.setId(PURCHASE_ID);
        purchase.setPurchaseNo("CG202610270001");
        purchase.setSupplierId(SUPPLIER_ID);
        purchase.setWarehouseId(WAREHOUSE_ID);
        purchase.setStatus(status);
        purchase.setApplicantId(applicantId);
        return purchase;
    }

    @Test
    @DisplayName("建单：金额服务端算，落「待审核」")
    void createComputesTotalAndPendsAudit() {
        loginAs(APPLICANT_ID);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(1));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(new BizWarehouse());
        when(drugMapper.selectById(DRUG_ID)).thenReturn(new BizDrug());

        PurchaseItemRequest item = new PurchaseItemRequest();
        item.setDrugId(DRUG_ID);
        item.setQuantity(50);
        item.setPrice(new BigDecimal("9.5"));
        PurchaseRequest request = new PurchaseRequest();
        request.setSupplierId(SUPPLIER_ID);
        request.setWarehouseId(WAREHOUSE_ID);
        request.setItems(List.of(item));

        Long id = service.create(request);

        assertThat(id).isEqualTo(PURCHASE_ID);
        ArgumentCaptor<BizPurchase> captor = ArgumentCaptor.forClass(BizPurchase.class);
        verify(purchaseMapper).insert(captor.capture());
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("475.00");
        assertThat(captor.getValue().getStatus()).isEqualTo(BizPurchase.STATUS_PENDING);
        assertThat(captor.getValue().getApplicantId()).isEqualTo(APPLICANT_ID);
    }

    @Test
    @DisplayName("停用供应商不能建采购单（40902）")
    void createRejectsDisabledSupplier() {
        loginAs(APPLICANT_ID);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(0));

        PurchaseRequest request = new PurchaseRequest();
        request.setSupplierId(SUPPLIER_ID);
        request.setWarehouseId(WAREHOUSE_ID);
        request.setItems(List.of(new PurchaseItemRequest()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40902);
    }

    @Test
    @DisplayName("申请人不能审自己的单（40902）")
    void auditRejectsSelfAudit() {
        loginAs(APPLICANT_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_PENDING, APPLICANT_ID));

        assertThatThrownBy(() -> service.audit(PURCHASE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40902);
        verify(purchaseMapper, never()).audit(anyLong(), anyLong());
    }

    @Test
    @DisplayName("审核通过只改状态，绝不动库存")
    void auditDoesNotTouchStock() {
        loginAs(AUDITOR_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_PENDING, APPLICANT_ID));
        when(purchaseMapper.audit(PURCHASE_ID, AUDITOR_ID)).thenReturn(1);

        service.audit(PURCHASE_ID);

        verify(purchaseMapper).audit(PURCHASE_ID, AUDITOR_ID);
        verify(stockMapper, never()).insert(any(BizStock.class));
        verify(stockMapper, never()).updateById(any(BizStock.class));
        verify(recordMapper, never()).insert(any(BizStockRecord.class));
    }

    @Test
    @DisplayName("非待审核状态不能审核（40902）")
    void auditRejectsWrongStatus() {
        loginAs(AUDITOR_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_DRAFT, APPLICANT_ID));

        assertThatThrownBy(() -> service.audit(PURCHASE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40902);
    }

    @Test
    @DisplayName("入库：加库存 + 写流水 + 回填实到 + 单据置已入库")
    void stockInDoesAllFour() {
        loginAs(APPLICANT_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_AUDITED, APPLICANT_ID));
        BizPurchaseItem item = new BizPurchaseItem();
        item.setId(ITEM_ID);
        item.setDrugId(DRUG_ID);
        item.setQuantity(50);
        item.setArriveNum(0);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(stockMapper.selectOne(any())).thenReturn(null);
        when(purchaseMapper.markStocked(PURCHASE_ID, APPLICANT_ID)).thenReturn(1);

        PurchaseInRequest request = new PurchaseInRequest();
        PurchaseInRequest.PurchaseInItemRequest line = new PurchaseInRequest.PurchaseInItemRequest();
        line.setItemId(ITEM_ID);
        line.setArriveNum(50);
        line.setBatchNo("B9");
        line.setExpireDate(LocalDate.now().plusYears(2));
        request.setItems(List.of(line));

        service.stockIn(PURCHASE_ID, request);

        verify(stockMapper).insert(any(BizStock.class));
        ArgumentCaptor<BizStockRecord> record = ArgumentCaptor.forClass(BizStockRecord.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getRecordType()).isEqualTo(1);
        assertThat(record.getValue().getBatchNo()).isEqualTo("B9");
        ArgumentCaptor<BizPurchaseItem> itemUpdate = ArgumentCaptor.forClass(BizPurchaseItem.class);
        verify(itemMapper).updateById(itemUpdate.capture());
        assertThat(itemUpdate.getValue().getArriveNum()).isEqualTo(50);
        verify(purchaseMapper).markStocked(PURCHASE_ID, APPLICANT_ID);
    }

    @Test
    @DisplayName("实到数量超过采购数量拒绝（40001）")
    void stockInRejectsOverArrival() {
        loginAs(APPLICANT_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_AUDITED, APPLICANT_ID));
        BizPurchaseItem item = new BizPurchaseItem();
        item.setId(ITEM_ID);
        item.setDrugId(DRUG_ID);
        item.setQuantity(50);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        PurchaseInRequest request = new PurchaseInRequest();
        PurchaseInRequest.PurchaseInItemRequest line = new PurchaseInRequest.PurchaseInItemRequest();
        line.setItemId(ITEM_ID);
        line.setArriveNum(51);
        line.setBatchNo("B9");
        line.setExpireDate(LocalDate.now().plusYears(2));
        request.setItems(List.of(line));

        assertThatThrownBy(() -> service.stockIn(PURCHASE_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
        verify(stockMapper, never()).insert(any(BizStock.class));
    }

    @Test
    @DisplayName("未审核的采购单不能入库（40902）")
    void stockInRejectsNotAudited() {
        loginAs(APPLICANT_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_PENDING, APPLICANT_ID));

        PurchaseInRequest request = new PurchaseInRequest();
        request.setItems(List.of(new PurchaseInRequest.PurchaseInItemRequest()));

        assertThatThrownBy(() -> service.stockIn(PURCHASE_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40902);
    }

    @Test
    @DisplayName("已入库的采购单不能作废（40902）")
    void voidRejectsStocked() {
        loginAs(APPLICANT_ID);
        when(purchaseMapper.selectById(PURCHASE_ID)).thenReturn(purchase(BizPurchase.STATUS_STOCKED, APPLICANT_ID));

        assertThatThrownBy(() -> service.voidPurchase(PURCHASE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40902);
        verify(purchaseMapper, never()).voidPurchase(anyLong(), anyLong());
    }
}
