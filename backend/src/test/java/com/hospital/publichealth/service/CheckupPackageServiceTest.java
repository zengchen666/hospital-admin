package com.hospital.publichealth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.LoginUser;
import com.hospital.finance.entity.BizTreatItem;
import com.hospital.finance.mapper.BizTreatItemMapper;
import com.hospital.publichealth.dto.CheckupPackageRequest;
import com.hospital.publichealth.dto.CheckupPackageVO;
import com.hospital.publichealth.entity.BizCheckupPackage;
import com.hospital.publichealth.mapper.BizCheckupPackageMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 体检套餐维护。
 *
 * <p>重点是 {@code itemIds} 的安全：它是「数字 + 逗号」的纯文本，
 * 放行任意字符等于开一个注入口子；查不到的项目 ID 也必须报错，
 * 否则体检单里会出现一个谁也不知道是什么的空项目。</p>
 */
@ExtendWith(MockitoExtension.class)
class CheckupPackageServiceTest {

    private static final Long PACKAGE_ID = 5001L;
    private static final Long OPERATOR_ID = 8L;

    @Mock
    private BizCheckupPackageMapper packageMapper;
    @Mock
    private BizTreatItemMapper treatItemMapper;

    private CheckupPackageService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizCheckupPackage.class);
        TableInfoHelper.initTableInfo(assistant, BizTreatItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new CheckupPackageService(packageMapper, treatItemMapper);
        loginAs(OPERATOR_ID);
        lenient().doAnswer(inv -> {
            BizCheckupPackage entity = inv.getArgument(0);
            entity.setId(PACKAGE_ID);
            return 1;
        }).when(packageMapper).insert(any(BizCheckupPackage.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("itemIds 含非数字字符时拒绝（40001）—— 那是注入口子")
    void illegalItemIdsRejected() {
        CheckupPackageRequest request = request("9010; DROP TABLE biz_checkup", new BigDecimal("680.00"));

        assertCode(() -> service.create(request), ErrorCode.PARAM_INVALID);
        verify(packageMapper, never()).insert(any(BizCheckupPackage.class));
    }

    @Test
    @DisplayName("itemIds 指向不存在的诊疗项目时报 40400")
    void unknownItemRejected() {
        when(treatItemMapper.selectById(9010L)).thenReturn(null);

        assertCode(() -> service.create(request("9010", new BigDecimal("680.00"))),
                ErrorCode.NO_RESOURCE);
        verify(packageMapper, never()).insert(any(BizCheckupPackage.class));
    }

    @Test
    @DisplayName("创建成功：itemIds 规范化，默认启用")
    void createSuccess() {
        when(treatItemMapper.selectById(9010L)).thenReturn(item(9010L, "血常规"));
        when(treatItemMapper.selectById(9011L)).thenReturn(item(9011L, "尿常规"));

        Long id = service.create(request("9010,9011", new BigDecimal("680.00")));

        assertThat(id).isEqualTo(PACKAGE_ID);
        ArgumentCaptor<BizCheckupPackage> captor = ArgumentCaptor.forClass(BizCheckupPackage.class);
        verify(packageMapper).insert(captor.capture());
        BizCheckupPackage saved = captor.getValue();
        assertThat(saved.getItemIds()).isEqualTo("9010,9011");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getCreateBy()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("不带项目的套餐也能建（只卖一个打包价）")
    void createWithoutItems() {
        service.create(request(null, new BigDecimal("680.00")));

        ArgumentCaptor<BizCheckupPackage> captor = ArgumentCaptor.forClass(BizCheckupPackage.class);
        verify(packageMapper).insert(captor.capture());
        assertThat(captor.getValue().getItemIds()).isNull();
    }

    @Test
    @DisplayName("更新不存在的套餐报 40400")
    void updateMissing() {
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(null);

        assertCode(() -> service.update(PACKAGE_ID, request("9010", new BigDecimal("680.00"))),
                ErrorCode.NO_RESOURCE);
        verify(packageMapper, never()).updateById(any(BizCheckupPackage.class));
    }

    @Test
    @DisplayName("更新成功：只改入参给了的字段")
    void updateSuccess() {
        BizCheckupPackage existing = new BizCheckupPackage();
        existing.setId(PACKAGE_ID);
        existing.setPackageName("职场基础体检");
        existing.setStatus(1);
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(existing);
        when(treatItemMapper.selectById(9010L)).thenReturn(item(9010L, "血常规"));

        service.update(PACKAGE_ID, request("9010", new BigDecimal("780.00")));

        ArgumentCaptor<BizCheckupPackage> captor = ArgumentCaptor.forClass(BizCheckupPackage.class);
        verify(packageMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("780.00");
        // 入参没给 status，保持原值
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getUpdateBy()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("启停只接受 0/1，其它值报 40001")
    void invalidStatusRejected() {
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(new BizCheckupPackage());

        assertCode(() -> service.changeStatus(PACKAGE_ID, 9), ErrorCode.PARAM_INVALID);
        verify(packageMapper, never()).updateById(any(BizCheckupPackage.class));
    }

    @Test
    @DisplayName("停用套餐只是改状态，不删记录（历史体检单还要能还原买了什么）")
    void disableKeepsRecord() {
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(new BizCheckupPackage());

        service.changeStatus(PACKAGE_ID, 0);

        ArgumentCaptor<BizCheckupPackage> captor = ArgumentCaptor.forClass(BizCheckupPackage.class);
        verify(packageMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        verify(packageMapper, never()).deleteById(any(BizCheckupPackage.class));
    }

    @Test
    @DisplayName("列表只返回启用套餐，并把项目 ID 翻译成中文名")
    void listEnabledWithItemNames() {
        BizCheckupPackage pkg = new BizCheckupPackage();
        pkg.setId(PACKAGE_ID);
        pkg.setPackageName("职场基础体检");
        pkg.setItemIds("9010,9011");
        pkg.setPrice(new BigDecimal("680.00"));
        pkg.setStatus(1);
        when(packageMapper.selectList(any())).thenReturn(List.of(pkg));
        when(treatItemMapper.selectById(9010L)).thenReturn(item(9010L, "血常规"));
        when(treatItemMapper.selectById(9011L)).thenReturn(item(9011L, "尿常规"));

        List<CheckupPackageVO> list = service.list(true, null);

        assertThat(list).hasSize(1);
        CheckupPackageVO vo = list.get(0);
        assertThat(vo.getItemIds()).isEqualTo("9010,9011");
        assertThat(vo.getItemNames()).containsExactly("血常规", "尿常规");
    }

    @Test
    @DisplayName("详情不存在时报 40400")
    void detailMissing() {
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(null);

        assertCode(() -> service.detail(PACKAGE_ID), ErrorCode.NO_RESOURCE);
    }

    /* ---------------- 辅助 ---------------- */

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizTreatItem item(Long id, String name) {
        BizTreatItem item = new BizTreatItem();
        item.setId(id);
        item.setItemName(name);
        return item;
    }

    private CheckupPackageRequest request(String itemIds, BigDecimal price) {
        CheckupPackageRequest request = new CheckupPackageRequest();
        request.setPackageName("职场基础体检");
        request.setItemIds(itemIds);
        request.setPrice(price);
        return request;
    }
}
