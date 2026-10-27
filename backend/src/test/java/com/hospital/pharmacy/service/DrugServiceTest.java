package com.hospital.pharmacy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.pharmacy.dto.DrugQuery;
import com.hospital.pharmacy.dto.DrugRequest;
import com.hospital.pharmacy.dto.StockInRequest;
import com.hospital.pharmacy.dto.StockOutRequest;
import com.hospital.pharmacy.dto.StockQuery;
import com.hospital.pharmacy.dto.StockVO;
import com.hospital.pharmacy.entity.BizDrug;
import com.hospital.pharmacy.entity.BizStock;
import com.hospital.pharmacy.entity.BizStockRecord;
import com.hospital.pharmacy.entity.BizWarehouse;
import com.hospital.pharmacy.mapper.BizDrugMapper;
import com.hospital.pharmacy.mapper.BizStockMapper;
import com.hospital.pharmacy.mapper.BizStockRecordMapper;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 药品与库存。
 *
 * <p>本类守住四条：</p>
 * <ol>
 *   <li>扣减走条件更新，余量不足时<b>不能写流水</b>；</li>
 *   <li>同一 药品+库房+批号 <b>累加</b>而不是新增一行；</li>
 *   <li>已过期批次 / 效期早于生产日期 一律拒绝入库；</li>
 *   <li>有库存的药品不允许删除。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class DrugServiceTest {

    private static final Long DRUG_ID = 9101L;
    private static final Long WAREHOUSE_ID = 8001L;
    private static final Long STOCK_ID = 9201L;
    private static final Long OPERATOR_ID = 7L;

    @Mock
    private BizDrugMapper drugMapper;
    @Mock
    private BizStockMapper stockMapper;
    @Mock
    private BizStockRecordMapper recordMapper;
    @Mock
    private BizWarehouseMapper warehouseMapper;
    @Mock
    private SysUserMapper userMapper;

    private DrugService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizDrug.class);
        TableInfoHelper.initTableInfo(assistant, BizStock.class);
        TableInfoHelper.initTableInfo(assistant, BizWarehouse.class);
    }

    @BeforeEach
    void setUp() {
        service = new DrugService(drugMapper, stockMapper, recordMapper, warehouseMapper, userMapper);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizDrug entity = inv.getArgument(0);
            entity.setId(DRUG_ID);
            return 1;
        }).when(drugMapper).insert(any(BizDrug.class));
        lenient().doAnswer(inv -> {
            BizStock entity = inv.getArgument(0);
            entity.setId(STOCK_ID);
            return 1;
        }).when(stockMapper).insert(any(BizStock.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("pharm01")
                .roleCodes(new LinkedHashSet<>(Set.of("pharm")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizDrug drug() {
        BizDrug drug = new BizDrug();
        drug.setId(DRUG_ID);
        drug.setDrugCode("D001");
        drug.setDrugName("阿莫西林胶囊");
        drug.setStatus(1);
        return drug;
    }

    private BizStock stock(int quantity, int warnNum) {
        BizStock stock = new BizStock();
        stock.setId(STOCK_ID);
        stock.setDrugId(DRUG_ID);
        stock.setWarehouseId(WAREHOUSE_ID);
        stock.setBatchNo("B1");
        stock.setQuantity(quantity);
        stock.setWarnNum(warnNum);
        stock.setExpireDate(LocalDate.now().plusYears(2));
        return stock;
    }

    private StockInRequest stockIn(int quantity, String batchNo) {
        StockInRequest request = new StockInRequest();
        request.setDrugId(DRUG_ID);
        request.setWarehouseId(WAREHOUSE_ID);
        request.setBatchNo(batchNo);
        request.setQuantity(quantity);
        request.setExpireDate(LocalDate.now().plusYears(1));
        return request;
    }

    @Test
    @DisplayName("入库：新批次插入，并写一条入库流水")
    void stockInInsertsAndWritesRecord() {
        loginAs(OPERATOR_ID);
        when(drugMapper.selectById(DRUG_ID)).thenReturn(drug());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(new BizWarehouse());
        when(stockMapper.selectOne(any())).thenReturn(null);

        service.stockIn(stockIn(100, "T1"));

        verify(stockMapper).insert(any(BizStock.class));
        ArgumentCaptor<BizStockRecord> captor = ArgumentCaptor.forClass(BizStockRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertThat(captor.getValue().getRecordType()).isEqualTo(1);
        assertThat(captor.getValue().getQuantity()).isEqualTo(100);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("入库：同批号累加而不是新增一行")
    void stockInAccumulatesSameBatch() {
        loginAs(OPERATOR_ID);
        when(drugMapper.selectById(DRUG_ID)).thenReturn(drug());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(new BizWarehouse());
        when(stockMapper.selectOne(any())).thenReturn(stock(40, 10));

        service.stockIn(stockIn(60, "B1"));

        ArgumentCaptor<BizStock> captor = ArgumentCaptor.forClass(BizStock.class);
        verify(stockMapper).updateById(captor.capture());
        verify(stockMapper, never()).insert(any(BizStock.class));
        assertThat(captor.getValue().getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("已过期批次拒绝入库（40001）")
    void stockInRejectsExpired() {
        loginAs(OPERATOR_ID);
        StockInRequest request = stockIn(10, "T2");
        request.setExpireDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.stockIn(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
        verify(stockMapper, never()).insert(any(BizStock.class));
    }

    @Test
    @DisplayName("有效期早于生产日期拒绝入库（40001）")
    void stockInRejectsExpireBeforeProduce() {
        loginAs(OPERATOR_ID);
        StockInRequest request = stockIn(10, "T3");
        request.setProduceDate(LocalDate.now().plusYears(1));
        request.setExpireDate(LocalDate.now().plusDays(30));

        assertThatThrownBy(() -> service.stockIn(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
    }

    @Test
    @DisplayName("出库：扣减失败时不写流水（账不能多出一条无来源的变动）")
    void stockOutSkipsRecordWhenDecreaseFails() {
        loginAs(OPERATOR_ID);
        when(stockMapper.selectOne(any())).thenReturn(stock(5, 10));
        when(stockMapper.decrease(STOCK_ID, 999)).thenReturn(0);

        StockOutRequest request = new StockOutRequest();
        request.setDrugId(DRUG_ID);
        request.setWarehouseId(WAREHOUSE_ID);
        request.setBatchNo("B1");
        request.setQuantity(999);

        assertThatThrownBy(() -> service.stockOut(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(42201);
        verify(recordMapper, never()).insert(any(BizStockRecord.class));
    }

    @Test
    @DisplayName("出库成功写一条出库流水")
    void stockOutWritesRecord() {
        loginAs(OPERATOR_ID);
        when(stockMapper.selectOne(any())).thenReturn(stock(100, 10));
        when(stockMapper.decrease(STOCK_ID, 30)).thenReturn(1);

        StockOutRequest request = new StockOutRequest();
        request.setDrugId(DRUG_ID);
        request.setWarehouseId(WAREHOUSE_ID);
        request.setBatchNo("B1");
        request.setQuantity(30);

        service.stockOut(request);

        ArgumentCaptor<BizStockRecord> captor = ArgumentCaptor.forClass(BizStockRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertThat(captor.getValue().getRecordType()).isEqualTo(2);
        assertThat(captor.getValue().getQuantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("有库存的药品不允许删除（40900）")
    void deleteRejectedWhenStockRemains() {
        when(drugMapper.selectById(DRUG_ID)).thenReturn(drug());
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(20, 10)));

        assertThatThrownBy(() -> service.deleteDrug(DRUG_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40900);
        verify(drugMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("新增药品：编码重复拒绝（40904）")
    void createRejectsDuplicateCode() {
        when(drugMapper.selectCount(any())).thenReturn(1L);

        DrugRequest request = new DrugRequest();
        request.setDrugCode("D001");
        request.setDrugName("阿莫西林胶囊");
        request.setUnit("盒");
        request.setPurchasePrice(new BigDecimal("10"));
        request.setSalePrice(new BigDecimal("18.5"));

        assertThatThrownBy(() -> service.createDrug(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40904);
    }

    @Test
    @DisplayName("库存分页：预警标记按 quantity <= warnNum 判定")
    void stockPageFlagsWarn() {
        Page<BizStock> page = new Page<>(1, 10);
        page.setRecords(List.of(stock(5, 10)));
        page.setTotal(1);
        when(stockMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(drugMapper.selectByIds(any())).thenReturn(List.of(drug()));
        BizWarehouse warehouse = new BizWarehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setWarehouseName("中心药库");
        when(warehouseMapper.selectByIds(any())).thenReturn(List.of(warehouse));

        StockQuery query = new StockQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<StockVO> result = service.pageStock(query);

        assertThat(result.getList().get(0).isWarn()).isTrue();
        assertThat(result.getList().get(0).getWarehouseName()).isEqualTo("中心药库");
    }

    @Test
    @DisplayName("库存分页：预警线为 0 的批次不参与预警（0 表示不预警）")
    void warnZeroMeansNoWarning() {
        Page<BizStock> page = new Page<>(1, 10);
        page.setRecords(List.of(stock(0, 0)));
        page.setTotal(1);
        when(stockMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(drugMapper.selectByIds(any())).thenReturn(List.of(drug()));
        when(warehouseMapper.selectByIds(any())).thenReturn(List.of(new BizWarehouse()));

        StockQuery query = new StockQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<StockVO> result = service.pageStock(query);

        assertThat(result.getList().get(0).isWarn()).isFalse();
    }
}
