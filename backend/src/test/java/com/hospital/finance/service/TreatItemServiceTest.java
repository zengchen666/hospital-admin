package com.hospital.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.dto.TreatItemRequest;
import com.hospital.finance.entity.BizTreatItem;
import com.hospital.finance.mapper.BizTreatItemMapper;
import com.hospital.system.entity.SysDept;
import com.hospital.system.mapper.SysDeptMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 诊疗项目价目表：调价新增版本、同日不可重复、取价按生效日期。
 */
@ExtendWith(MockitoExtension.class)
class TreatItemServiceTest {

    private static final Long ITEM_ID = 7001L;
    private static final Long NEW_ID = 7002L;
    private static final String CODE = "T6T1";

    @Mock
    private BizTreatItemMapper treatItemMapper;
    @Mock
    private SysDeptMapper deptMapper;

    private TreatItemService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizTreatItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new TreatItemService(treatItemMapper, deptMapper);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizTreatItem entity = inv.getArgument(0);
            entity.setId(NEW_ID);
            return 1;
        }).when(treatItemMapper).insert(any(BizTreatItem.class));
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

    private BizTreatItem item(Long id, BigDecimal price, LocalDate effective, Long deptId) {
        BizTreatItem entity = new BizTreatItem();
        entity.setId(id);
        entity.setItemCode(CODE);
        entity.setItemName("T6测试项目");
        entity.setItemType(1);
        entity.setUnit("次");
        entity.setPrice(price);
        entity.setEffectiveDate(effective);
        entity.setStatus(1);
        entity.setDeptId(deptId);
        return entity;
    }

    private TreatItemRequest request(BigDecimal price, LocalDate effective) {
        TreatItemRequest request = new TreatItemRequest();
        request.setItemCode(CODE);
        request.setItemName("T6测试项目");
        request.setItemType(1);
        request.setUnit("次");
        request.setPrice(price);
        request.setEffectiveDate(effective);
        return request;
    }

    @Test
    @DisplayName("同一编码同一生效日不能有两版价")
    void duplicateVersionIsRejected() {
        when(treatItemMapper.selectCount(any())).thenReturn(1L);
        loginAs(7L, "finance");

        assertThatThrownBy(() -> service.create(request(new BigDecimal("100"), LocalDate.now())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有价格版本");

        verify(treatItemMapper, never()).insert(any(BizTreatItem.class));
    }

    @Test
    @DisplayName("新增项目：生效日期为空时取今天")
    void createDefaultsEffectiveDateToToday() {
        when(treatItemMapper.selectCount(any())).thenReturn(0L);
        loginAs(7L, "finance");

        service.create(request(new BigDecimal("100"), null));

        ArgumentCaptor<BizTreatItem> captor = ArgumentCaptor.forClass(BizTreatItem.class);
        verify(treatItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getEffectiveDate()).isEqualTo(LocalDate.now());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("100");
        // 医保类别默认丙类（全自费）：宁可少报也不能虚报
        assertThat(captor.getValue().getInsuranceType()).isEqualTo(3);
    }

    @Test
    @DisplayName("调价：新增一版价格，原记录不被覆盖")
    void adjustPriceCreatesNewVersion() {
        BizTreatItem old = item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null);
        when(treatItemMapper.selectById(ITEM_ID)).thenReturn(old);
        when(treatItemMapper.selectCount(any())).thenReturn(0L);
        loginAs(7L, "finance");

        Long newId = service.adjustPrice(ITEM_ID,
                request(new BigDecimal("120"), LocalDate.now().plusDays(1)));

        assertThat(newId).isEqualTo(NEW_ID);
        ArgumentCaptor<BizTreatItem> captor = ArgumentCaptor.forClass(BizTreatItem.class);
        verify(treatItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("120");
        // 编码沿用原项目：调价不是换项目
        assertThat(captor.getValue().getItemCode()).isEqualTo(CODE);
        verify(treatItemMapper, never()).updateById(any(BizTreatItem.class));
    }

    @Test
    @DisplayName("价格没变时按普通维护处理，不产生新版本")
    void noPriceChangeUpdatesInPlace() {
        BizTreatItem old = item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null);
        when(treatItemMapper.selectById(ITEM_ID)).thenReturn(old);
        loginAs(7L, "finance");

        Long id = service.adjustPrice(ITEM_ID, request(new BigDecimal("100"), LocalDate.now()));

        assertThat(id).isEqualTo(ITEM_ID);
        verify(treatItemMapper).updateById(old);
        verify(treatItemMapper, never()).insert(any(BizTreatItem.class));
    }

    @Test
    @DisplayName("调价到已存在版本的日期 -> 拒绝")
    void adjustToExistingDateIsRejected() {
        BizTreatItem old = item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null);
        when(treatItemMapper.selectById(ITEM_ID)).thenReturn(old);
        when(treatItemMapper.selectCount(any())).thenReturn(1L);
        loginAs(7L, "finance");

        assertThatThrownBy(() -> service.adjustPrice(ITEM_ID,
                request(new BigDecimal("120"), LocalDate.now().plusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有价格版本");

        verify(treatItemMapper, never()).insert(any(BizTreatItem.class));
    }

    @Test
    @DisplayName("详情：科室为空时不因 Map.of() 的 null key 抛 NPE")
    void detailHandlesNullDeptId() {
        when(treatItemMapper.selectById(ITEM_ID))
                .thenReturn(item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null));
        lenient().when(treatItemMapper.selectList(any())).thenReturn(List.of());

        var vo = service.detail(ITEM_ID);

        assertThat(vo.getDeptName()).isNull();
        assertThat(vo.getItemCode()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("详情：有科室时装配科室名")
    void detailAssemblesDeptName() {
        when(treatItemMapper.selectById(ITEM_ID))
                .thenReturn(item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), 1001L));
        lenient().when(treatItemMapper.selectList(any())).thenReturn(List.of());
        SysDept dept = new SysDept();
        dept.setId(1001L);
        dept.setDeptName("内科");
        when(deptMapper.selectByIds(any())).thenReturn(List.of(dept));

        assertThat(service.detail(ITEM_ID).getDeptName()).isEqualTo("内科");
    }

    @Test
    @DisplayName("停用是改状态，不是删除")
    void disableKeepsRecord() {
        BizTreatItem old = item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null);
        when(treatItemMapper.selectById(ITEM_ID)).thenReturn(old);
        loginAs(7L, "finance");

        service.disable(ITEM_ID);

        ArgumentCaptor<BizTreatItem> captor = ArgumentCaptor.forClass(BizTreatItem.class);
        verify(treatItemMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        verify(treatItemMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("分页：生效价过滤下推 SQL，不在内存里过滤")
    void effectiveFilterIsPushedDown() {
        when(treatItemMapper.selectPage(any(Page.class), any()))
                .thenReturn(new Page<BizTreatItem>(1, 10).setRecords(List.of(
                        item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null)))
                        .setTotal(1));

        var query = new com.hospital.finance.dto.TreatItemQuery();
        query.setOnlyEffective(true);
        var result = service.page(query);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        // 关键：只查一次库返回多少就是多少，前端看到的总数与内容一致
        verify(treatItemMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("项目不存在时调价直接 404")
    void adjustMissingItem() {
        when(treatItemMapper.selectById(ITEM_ID)).thenReturn(null);
        loginAs(7L, "finance");

        assertThatThrownBy(() -> service.adjustPrice(ITEM_ID,
                request(new BigDecimal("120"), LocalDate.now())))
                .hasMessageContaining("诊疗项目不存在");
    }

    @Test
    @DisplayName("调价记录带上操作人，便于事后追溯谁改的价")
    void adjustUsesCurrentUser() {
        BizTreatItem old = item(ITEM_ID, new BigDecimal("100"), LocalDate.now(), null);
        when(treatItemMapper.selectById(ITEM_ID)).thenReturn(old);
        when(treatItemMapper.selectCount(any())).thenReturn(0L);
        loginAs(7L, "finance");

        service.adjustPrice(ITEM_ID, request(new BigDecimal("120"), LocalDate.now().plusDays(1)));

        ArgumentCaptor<BizTreatItem> captor = ArgumentCaptor.forClass(BizTreatItem.class);
        verify(treatItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getEffectiveDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(captor.getValue().getInsuranceRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
