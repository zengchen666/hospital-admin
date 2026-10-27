package com.hospital.publichealth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.LoginUser;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.publichealth.dto.CheckupQuery;
import com.hospital.publichealth.dto.CheckupReportRequest;
import com.hospital.publichealth.dto.CheckupRequest;
import com.hospital.publichealth.dto.CheckupVO;
import com.hospital.publichealth.entity.BizCheckup;
import com.hospital.publichealth.entity.BizCheckupPackage;
import com.hospital.publichealth.mapper.BizCheckupMapper;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 体检登记与报告出具。
 *
 * <p>两条必须锁死：套餐名与价格<b>存快照</b>（套餐改价不影响历史单）；
 * 出报告<b>只能从「已检」走</b>（待检直接出报告等于伪造体检结果）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CheckupServiceTest {

    private static final Long PATIENT_ID = 100L;
    private static final Long CHECKUP_ID = 6301L;
    private static final Long PACKAGE_ID = 5001L;
    private static final Long OPERATOR_ID = 8L;

    @Mock
    private BizCheckupMapper checkupMapper;
    @Mock
    private BizCheckupPackageMapper packageMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private CheckupNoGenerator noGenerator;

    private CheckupService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizCheckup.class);
        TableInfoHelper.initTableInfo(assistant, BizCheckupPackage.class);
        TableInfoHelper.initTableInfo(assistant, BizPatient.class);
    }

    @BeforeEach
    void setUp() {
        service = new CheckupService(checkupMapper, packageMapper, patientMapper, noGenerator);
        loginAs(OPERATOR_ID);
        lenient().doAnswer(inv -> {
            BizCheckup entity = inv.getArgument(0);
            entity.setId(CHECKUP_ID);
            return 1;
        }).when(checkupMapper).insert(any(BizCheckup.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("患者不存在时拒绝登记（40400）")
    void patientNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertCode(() -> service.create(request(PACKAGE_ID)), ErrorCode.NO_RESOURCE);
        verify(checkupMapper, never()).insert(any(BizCheckup.class));
    }

    @Test
    @DisplayName("体检日期不能是未来（40001）")
    void futureDateRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());

        CheckupRequest request = request(PACKAGE_ID);
        request.setCheckupDate(LocalDate.now().plusDays(1));

        assertCode(() -> service.create(request), ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("套餐不存在时报 40400")
    void packageNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(null);

        assertCode(() -> service.create(request(PACKAGE_ID)), ErrorCode.NO_RESOURCE);
    }

    @Test
    @DisplayName("停用套餐不能被新单选中（40902）")
    void disabledPackageRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(pkg(0, new BigDecimal("680.00")));

        assertCode(() -> service.create(request(PACKAGE_ID)), ErrorCode.STATUS_NOT_ALLOWED);
    }

    @Test
    @DisplayName("登记成功：套餐名与价格存快照，状态为 0 待检")
    void createSnapshot() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(pkg(1, new BigDecimal("680.00")));
        when(noGenerator.next()).thenReturn("TJ202610270001");

        Long id = service.create(request(PACKAGE_ID));

        assertThat(id).isEqualTo(CHECKUP_ID);
        ArgumentCaptor<BizCheckup> captor = ArgumentCaptor.forClass(BizCheckup.class);
        verify(checkupMapper).insert(captor.capture());
        BizCheckup saved = captor.getValue();
        assertThat(saved.getCheckupNo()).isEqualTo("TJ202610270001");
        assertThat(saved.getPackageName()).isEqualTo("职场基础体检");
        assertThat(saved.getAmount()).isEqualByComparingTo("680.00");
        assertThat(saved.getStatus()).isZero();
    }

    @Test
    @DisplayName("散查（无套餐）时不写套餐名，金额记 0")
    void createWithoutPackage() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(noGenerator.next()).thenReturn("TJ202610270002");

        service.create(request(null));

        ArgumentCaptor<BizCheckup> captor = ArgumentCaptor.forClass(BizCheckup.class);
        verify(checkupMapper).insert(captor.capture());
        assertThat(captor.getValue().getPackageId()).isNull();
        assertThat(captor.getValue().getPackageName()).isNull();
    }

    @Test
    @DisplayName("标记已检：0 → 1")
    void completeSuccess() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(0));
        when(checkupMapper.transitStatus(CHECKUP_ID, 0, 1, OPERATOR_ID)).thenReturn(1);

        service.complete(CHECKUP_ID);

        verify(checkupMapper).transitStatus(CHECKUP_ID, 0, 1, OPERATOR_ID);
    }

    @Test
    @DisplayName("已完成检查的不能重复标记（40902）")
    void completeTwice() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(1));

        assertCode(() -> service.complete(CHECKUP_ID), ErrorCode.STATUS_NOT_ALLOWED);
        verify(checkupMapper, never()).transitStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("并发重复标记时条件更新返回 0 → 40900")
    void completeConflict() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(0));
        when(checkupMapper.transitStatus(CHECKUP_ID, 0, 1, OPERATOR_ID)).thenReturn(0);

        assertCode(() -> service.complete(CHECKUP_ID), ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("未检就出报告被拒（40902）—— 那是伪造体检结果")
    void reportBeforeCheckup() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(0));

        assertCode(() -> service.publishReport(CHECKUP_ID, reportRequest("一切正常")),
                ErrorCode.STATUS_NOT_ALLOWED);
        verify(checkupMapper, never()).publishReport(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("已出报告的不能重复出具（40902）")
    void reportTwice() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(2));

        assertCode(() -> service.publishReport(CHECKUP_ID, reportRequest("一切正常")),
                ErrorCode.STATUS_NOT_ALLOWED);
    }

    @Test
    @DisplayName("出报告成功：写入结论与异常项，状态置 2")
    void publishSuccess() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(1));
        when(checkupMapper.publishReport(CHECKUP_ID, "血压偏高，建议复查", "血压偏高", OPERATOR_ID))
                .thenReturn(1);

        service.publishReport(CHECKUP_ID, reportRequest("血压偏高，建议复查"));

        verify(checkupMapper).publishReport(CHECKUP_ID, "血压偏高，建议复查", "血压偏高", OPERATOR_ID);
    }

    @Test
    @DisplayName("并发出具报告时条件更新返回 0 → 40900")
    void publishConflict() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(1));
        when(checkupMapper.publishReport(anyLong(), any(), any(), any())).thenReturn(0);

        assertCode(() -> service.publishReport(CHECKUP_ID, reportRequest("一切正常")),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("异常项支持中文逗号并去掉空白项")
    void abnormalNormalized() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(entity(1));
        when(checkupMapper.publishReport(CHECKUP_ID, "血压偏高", "血压偏高,血脂异常", OPERATOR_ID))
                .thenReturn(1);

        CheckupReportRequest request = reportRequest("血压偏高");
        request.setAbnormalItems("血压偏高， 血脂异常 , ");
        service.publishReport(CHECKUP_ID, request);

        verify(checkupMapper).publishReport(CHECKUP_ID, "血压偏高", "血压偏高,血脂异常", OPERATOR_ID);
    }

    @Test
    @DisplayName("详情不存在时报 40400")
    void detailMissing() {
        when(checkupMapper.selectById(CHECKUP_ID)).thenReturn(null);

        assertCode(() -> service.detail(CHECKUP_ID), ErrorCode.NO_RESOURCE);
    }

    @Test
    @DisplayName("列表取金额快照，套餐后来调价不影响历史单")
    void pageUsesSnapshotAmount() {
        BizCheckup checkup = entity(2);
        checkup.setPackageName("职场基础体检（旧名）");
        checkup.setAmount(new BigDecimal("680.00"));
        Page<BizCheckup> page = new Page<>(1, 10);
        page.setRecords(List.of(checkup));
        page.setTotal(1);
        when(checkupMapper.selectPage(any(), any())).thenReturn(page);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));

        PageResult<CheckupVO> result = service.page(new CheckupQuery());

        CheckupVO vo = result.getList().get(0);
        assertThat(vo.getPackageName()).isEqualTo("职场基础体检（旧名）");
        // 快照是 680，即使套餐当前价已调到 980 也不变
        assertThat(vo.getAmount()).isEqualByComparingTo("680.00");
        // 金额不必回查套餐表 —— 快照就在体检单上
        verify(packageMapper, never()).selectByIds(any());
    }

    /* ---------------- 辅助 ---------------- */

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("doc01").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPatient patient() {
        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("郑新患");
        patient.setGender(1);
        patient.setBirthDate(LocalDate.of(1988, 6, 6));
        return patient;
    }

    private BizCheckupPackage pkg(Integer status, BigDecimal price) {
        BizCheckupPackage pkg = new BizCheckupPackage();
        pkg.setId(PACKAGE_ID);
        pkg.setPackageName("职场基础体检");
        pkg.setPrice(price);
        pkg.setStatus(status);
        return pkg;
    }

    private BizCheckup entity(Integer status) {
        BizCheckup entity = new BizCheckup();
        entity.setId(CHECKUP_ID);
        entity.setCheckupNo("TJ202610270001");
        entity.setPatientId(PATIENT_ID);
        entity.setPackageId(PACKAGE_ID);
        entity.setPackageName("职场基础体检");
        entity.setCheckupDate(LocalDate.now());
        entity.setStatus(status);
        return entity;
    }

    private CheckupRequest request(Long packageId) {
        CheckupRequest request = new CheckupRequest();
        request.setPatientId(PATIENT_ID);
        request.setPackageId(packageId);
        request.setCheckupDate(LocalDate.now());
        return request;
    }

    private CheckupReportRequest reportRequest(String summary) {
        CheckupReportRequest request = new CheckupReportRequest();
        request.setSummary(summary);
        request.setAbnormalItems("血压偏高");
        return request;
    }
}
