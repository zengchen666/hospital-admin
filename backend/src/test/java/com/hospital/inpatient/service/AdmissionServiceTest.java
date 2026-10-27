package com.hospital.inpatient.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.inpatient.dto.AdmissionRequest;
import com.hospital.inpatient.dto.AdmissionVO;
import com.hospital.inpatient.dto.BedQuery;
import com.hospital.inpatient.dto.BedVO;
import com.hospital.inpatient.dto.WardVO;
import com.hospital.inpatient.entity.BizAdmission;
import com.hospital.inpatient.entity.BizBed;
import com.hospital.inpatient.entity.BizWard;
import com.hospital.inpatient.mapper.BizAdmissionMapper;
import com.hospital.inpatient.mapper.BizBedMapper;
import com.hospital.inpatient.mapper.BizWardMapper;
import com.hospital.patient.entity.BizPatient;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 住院登记：入院、分床、出院、预交金、床位图。
 *
 * <p>本类真正要守住的是<b>床位并发</b>：占用/释放必须走条件更新，
 * 且「先占新床、再放旧床」的顺序不能颠倒 —— 否则会出现一床两人，
 * 或者「新床被抢、旧床也丢了」的在院患者。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    private static final Long PATIENT_ID = 100L;
    private static final Long BED_ID = 8201L;
    private static final Long NEW_BED_ID = 8202L;
    private static final Long WARD_ID = 9001L;
    private static final Long ADMISSION_ID = 7001L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long OPERATOR_ID = 8L;

    @Mock
    private BizAdmissionMapper admissionMapper;
    @Mock
    private BizBedMapper bedMapper;
    @Mock
    private BizWardMapper wardMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private AdmissionNoGenerator noGenerator;

    private AdmissionService service;

    /** 分页与 lambda wrapper 都要 TableInfo 缓存，纯单测需要手动建。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizAdmission.class);
        TableInfoHelper.initTableInfo(assistant, BizBed.class);
        TableInfoHelper.initTableInfo(assistant, BizWard.class);
    }

    @BeforeEach
    void setUp() {
        service = new AdmissionService(admissionMapper, bedMapper, wardMapper,
                patientMapper, userMapper, noGenerator);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizAdmission entity = inv.getArgument(0);
            entity.setId(ADMISSION_ID);
            return 1;
        }).when(admissionMapper).insert(any(BizAdmission.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("nurse")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPatient patient() {
        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("郑新患");
        return patient;
    }

    private BizBed bed(Long id, String bedNo, Integer status) {
        BizBed bed = new BizBed();
        bed.setId(id);
        bed.setWardId(WARD_ID);
        bed.setBedNo(bedNo);
        bed.setBedType(1);
        bed.setStatus(status);
        return bed;
    }

    private BizAdmission admission(Integer status, Long bedId) {
        BizAdmission admission = new BizAdmission();
        admission.setId(ADMISSION_ID);
        admission.setAdmissionNo("ZY202610260001");
        admission.setPatientId(PATIENT_ID);
        admission.setBedId(bedId);
        admission.setDoctorId(DOCTOR_ID);
        admission.setAdmissionTime(LocalDateTime.now().minusDays(3));
        admission.setDeposit(new BigDecimal("1000.00"));
        admission.setStatus(status);
        return admission;
    }

    private AdmissionRequest request(Long bedId) {
        AdmissionRequest request = new AdmissionRequest();
        request.setPatientId(PATIENT_ID);
        request.setDeptId(1L);
        request.setBedId(bedId);
        request.setDoctorId(DOCTOR_ID);
        request.setDiagnosis("社区获得性肺炎");
        request.setDeposit(new BigDecimal("1500.00"));
        return request;
    }

    /* ---------------- 入院登记 ---------------- */

    @Test
    @DisplayName("患者不存在时拒绝入院（40400）")
    void patientNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.admit(request(BED_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("患者不存在");

        verify(admissionMapper, never()).insert(any(BizAdmission.class));
        verify(bedMapper, never()).occupy(anyLong());
    }

    @Test
    @DisplayName("已在院的患者不能重复入院（40900）")
    void alreadyInHospital() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(admissionMapper.countInHospital(PATIENT_ID)).thenReturn(1);

        assertThatThrownBy(() -> service.admit(request(BED_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已在院");

        verify(bedMapper, never()).occupy(anyLong());
        verify(admissionMapper, never()).insert(any(BizAdmission.class));
    }

    @Test
    @DisplayName("入院成功：先占床再落记录，且状态为在院")
    void admitOccupiesBedBeforeInsert() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(admissionMapper.countInHospital(PATIENT_ID)).thenReturn(0);
        when(bedMapper.selectById(BED_ID)).thenReturn(bed(BED_ID, "A01", 0));
        when(bedMapper.occupy(BED_ID)).thenReturn(1);
        when(noGenerator.next()).thenReturn("ZY202610260001");

        Long id = service.admit(request(BED_ID));

        assertThat(id).isEqualTo(ADMISSION_ID);
        // 顺序不能反：先占床，冲突尽早暴露
        InOrder order = inOrder(bedMapper, admissionMapper);
        order.verify(bedMapper).occupy(BED_ID);
        order.verify(admissionMapper).insert(any(BizAdmission.class));

        ArgumentCaptor<BizAdmission> captor = ArgumentCaptor.forClass(BizAdmission.class);
        verify(admissionMapper).insert(captor.capture());
        BizAdmission saved = captor.getValue();
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getAdmissionNo()).isEqualTo("ZY202610260001");
        assertThat(saved.getBedId()).isEqualTo(BED_ID);
        assertThat(saved.getDeposit()).isEqualByComparingTo("1500.00");
        assertThat(saved.getAdmissionTime()).isNotNull();
    }

    @Test
    @DisplayName("床位不存在时报 40400 而不是把占用失败当成冲突")
    void bedNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(admissionMapper.countInHospital(PATIENT_ID)).thenReturn(0);
        when(bedMapper.selectById(BED_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.admit(request(BED_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("床位不存在");

        verify(bedMapper, never()).occupy(anyLong());
    }

    @Test
    @DisplayName("床位被并发占用时抛 40900 且不落住院记录")
    void bedOccupiedConflict() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(admissionMapper.countInHospital(PATIENT_ID)).thenReturn(0);
        when(bedMapper.selectById(BED_ID)).thenReturn(bed(BED_ID, "A01", 1));
        when(bedMapper.occupy(BED_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.admit(request(BED_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被占用");

        verify(admissionMapper, never()).insert(any(BizAdmission.class));
    }

    @Test
    @DisplayName("急诊可先登记后分床：bedId 为空时不占床")
    void admitWithoutBed() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(admissionMapper.countInHospital(PATIENT_ID)).thenReturn(0);
        when(noGenerator.next()).thenReturn("ZY202610260002");

        Long id = service.admit(request(null));

        assertThat(id).isEqualTo(ADMISSION_ID);
        verify(bedMapper, never()).occupy(anyLong());
        ArgumentCaptor<BizAdmission> captor = ArgumentCaptor.forClass(BizAdmission.class);
        verify(admissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getBedId()).isNull();
    }

    @Test
    @DisplayName("预交金不传时按 0 入库，不产生 NPE")
    void depositDefaultsToZero() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(admissionMapper.countInHospital(PATIENT_ID)).thenReturn(0);
        when(noGenerator.next()).thenReturn("ZY202610260003");
        AdmissionRequest request = request(null);
        request.setDeposit(null);

        service.admit(request);

        ArgumentCaptor<BizAdmission> captor = ArgumentCaptor.forClass(BizAdmission.class);
        verify(admissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeposit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /* ---------------- 分床 / 换床 ---------------- */

    @Test
    @DisplayName("换床：先占新床，再释放旧床")
    void assignBedOccupiesNewBeforeReleasingOld() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(bedMapper.selectById(NEW_BED_ID)).thenReturn(bed(NEW_BED_ID, "A02", 0));
        when(bedMapper.occupy(NEW_BED_ID)).thenReturn(1);

        service.assignBed(ADMISSION_ID, NEW_BED_ID);

        InOrder order = inOrder(bedMapper, admissionMapper);
        order.verify(bedMapper).occupy(NEW_BED_ID);
        order.verify(admissionMapper).updateById(any(BizAdmission.class));
        order.verify(bedMapper).release(BED_ID);
    }

    @Test
    @DisplayName("新床被抢时旧床不能释放，否则患者变成没床的在院患者")
    void assignBedKeepsOldWhenNewBedTaken() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(bedMapper.selectById(NEW_BED_ID)).thenReturn(bed(NEW_BED_ID, "A02", 1));
        when(bedMapper.occupy(NEW_BED_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.assignBed(ADMISSION_ID, NEW_BED_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被占用");

        verify(bedMapper, never()).release(BED_ID);
        verify(admissionMapper, never()).updateById(any(BizAdmission.class));
    }

    @Test
    @DisplayName("分到同一张床时直接返回，不做无意义的占放")
    void assignSameBedIsNoop() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));

        service.assignBed(ADMISSION_ID, BED_ID);

        verify(bedMapper, never()).occupy(anyLong());
        verify(bedMapper, never()).release(anyLong());
        verify(admissionMapper, never()).updateById(any(BizAdmission.class));
    }

    @Test
    @DisplayName("已出院的记录不能分床（40902）")
    void assignBedRejectedAfterDischarge() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(1, BED_ID));

        assertThatThrownBy(() -> service.assignBed(ADMISSION_ID, NEW_BED_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已出院");

        verify(bedMapper, never()).occupy(anyLong());
    }

    /* ---------------- 出院 ---------------- */

    @Test
    @DisplayName("出院必须同时置状态并释放床位")
    void dischargeReleasesBed() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(admissionMapper.discharge(ADMISSION_ID, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "nurse");

        service.discharge(ADMISSION_ID);

        verify(admissionMapper).discharge(ADMISSION_ID, OPERATOR_ID);
        verify(bedMapper).release(BED_ID);
    }

    @Test
    @DisplayName("出院时状态被并发改变抛 40900，且不能释放床位")
    void dischargeConflictKeepsBed() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(admissionMapper.discharge(anyLong(), any())).thenReturn(0);
        loginAs(OPERATOR_ID, "nurse");

        assertThatThrownBy(() -> service.discharge(ADMISSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("住院状态已变化");

        verify(bedMapper, never()).release(anyLong());
    }

    @Test
    @DisplayName("重复出院被拒绝，提示已出院")
    void dischargeTwiceRejected() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(1, BED_ID));

        assertThatThrownBy(() -> service.discharge(ADMISSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已出院");

        verify(admissionMapper, never()).discharge(anyLong(), any());
    }

    @Test
    @DisplayName("未分床的住院出院时不调释放")
    void dischargeWithoutBedSkipsRelease() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, null));
        when(admissionMapper.discharge(ADMISSION_ID, null)).thenReturn(1);

        service.discharge(ADMISSION_ID);

        verify(bedMapper, never()).release(anyLong());
    }

    /* ---------------- 预交金 ---------------- */

    @Test
    @DisplayName("预交金充值 0 或负数被拒绝（40001）")
    void depositMustBePositive() {
        assertThatThrownBy(() -> service.addDeposit(ADMISSION_ID, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("充值金额必须大于 0");

        assertThatThrownBy(() -> service.addDeposit(ADMISSION_ID, new BigDecimal("-100")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("充值金额必须大于 0");

        assertThatThrownBy(() -> service.addDeposit(ADMISSION_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("充值金额必须大于 0");

        verify(admissionMapper, never()).addDeposit(anyLong(), any(), any());
    }

    @Test
    @DisplayName("已出院不能充值预交金（40902）")
    void depositRejectedAfterDischarge() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(1, BED_ID));

        assertThatThrownBy(() -> service.addDeposit(ADMISSION_ID, new BigDecimal("500")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已出院");

        verify(admissionMapper, never()).addDeposit(anyLong(), any(), any());
    }

    @Test
    @DisplayName("充值并发失败抛 40900")
    void depositConflict() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(admissionMapper.addDeposit(anyLong(), any(), any())).thenReturn(0);
        loginAs(OPERATOR_ID, "nurse");

        assertThatThrownBy(() -> service.addDeposit(ADMISSION_ID, new BigDecimal("500")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("住院状态已变化");
    }

    /* ---------------- 查询 ---------------- */

    @Test
    @DisplayName("住院记录不存在时明细报 40400")
    void detailNotFound() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(ADMISSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("住院记录不存在");
    }

    @Test
    @DisplayName("明细带出床位/病区/住院天数")
    void detailEnrichesBedAndStayDays() {
        BizAdmission admission = admission(0, BED_ID);
        admission.setAdmissionTime(LocalDateTime.now().minusDays(3));
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(bedMapper.selectByIds(any())).thenReturn(List.of(bed(BED_ID, "A01", 1)));
        BizWard ward = new BizWard();
        ward.setId(WARD_ID);
        ward.setWardName("内科一病区");
        when(wardMapper.selectByIds(any())).thenReturn(List.of(ward));

        AdmissionVO vo = service.detail(ADMISSION_ID);

        assertThat(vo.getBedNo()).isEqualTo("A01");
        assertThat(vo.getWardName()).isEqualTo("内科一病区");
        assertThat(vo.getPatientName()).isEqualTo("郑新患");
        assertThat(vo.getStayDays()).isEqualTo(3L);
    }

    @Test
    @DisplayName("床位图反查占用者姓名，空闲床为 null")
    void bedsMapResolvesOccupant() {
        Page<BizBed> page = new Page<>(1, 10);
        page.setRecords(List.of(bed(BED_ID, "A01", 1), bed(NEW_BED_ID, "A02", 0)));
        page.setTotal(2);
        when(bedMapper.selectPage(any(), any())).thenReturn(page);
        when(admissionMapper.selectList(any())).thenReturn(List.of(admission(0, BED_ID)));
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        BizWard ward = new BizWard();
        ward.setId(WARD_ID);
        ward.setWardName("内科一病区");
        when(wardMapper.selectByIds(any())).thenReturn(List.of(ward));

        BedQuery query = new BedQuery();
        query.setWardId(WARD_ID);
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<BedVO> result = service.beds(query);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList().get(0).getOccupantName()).isEqualTo("郑新患");
        assertThat(result.getList().get(1).getOccupantName()).isNull();
        assertThat(result.getList().get(0).getWardName()).isEqualTo("内科一病区");
    }

    /* ---------------- 转科 ---------------- */

    @Test
    @DisplayName("转科换科室并释放原床位（新病区的床由对方重新分配）")
    void transferReleasesOldBed() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(admissionMapper.transfer(ADMISSION_ID, 1002L, DOCTOR_ID, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "doctor");

        service.transfer(ADMISSION_ID, 1002L, DOCTOR_ID);

        verify(admissionMapper).transfer(ADMISSION_ID, 1002L, DOCTOR_ID, OPERATOR_ID);
        verify(bedMapper).release(BED_ID);
    }

    @Test
    @DisplayName("转科并发失败时不释放床位，避免患者两头都没床")
    void transferConflictKeepsBed() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));
        when(admissionMapper.transfer(ADMISSION_ID, 1002L, DOCTOR_ID, null)).thenReturn(0);

        assertThatThrownBy(() -> service.transfer(ADMISSION_ID, 1002L, DOCTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40900);

        verify(bedMapper, never()).release(anyLong());
    }

    @Test
    @DisplayName("已出院不能转科（40902）")
    void transferRejectedAfterDischarge() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(1, BED_ID));

        assertThatThrownBy(() -> service.transfer(ADMISSION_ID, 1002L, DOCTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已出院");

        verify(admissionMapper, never()).transfer(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("目标科室与接收医生都不填 -> 40001")
    void transferRequiresTarget() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0, BED_ID));

        assertThatThrownBy(() -> service.transfer(ADMISSION_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
    }

    /* ---------------- 病区 / 可分配床位 ---------------- */

    @Test
    @DisplayName("病区列表只返回启用中的病区")
    void wardsOnlyEnabled() {
        BizWard enabled = new BizWard();
        enabled.setId(WARD_ID);
        enabled.setWardName("内科一病区");
        enabled.setWardCode("WARD_NK1");
        enabled.setStatus(1);
        when(wardMapper.selectList(any())).thenReturn(List.of(enabled));

        List<WardVO> wards = service.wards();

        assertThat(wards).hasSize(1);
        assertThat(wards.get(0).getWardName()).isEqualTo("内科一病区");
    }

    @Test
    @DisplayName("可分配床位只列空闲床，不分页")
    void availableBedsOnlyFree() {
        when(bedMapper.selectList(any())).thenReturn(List.of(bed(BED_ID, "A01", 0)));
        BizWard ward = new BizWard();
        ward.setId(WARD_ID);
        ward.setWardName("内科一病区");
        when(wardMapper.selectByIds(any())).thenReturn(List.of(ward));

        List<BedVO> beds = service.availableBeds(WARD_ID);

        assertThat(beds).hasSize(1);
        assertThat(beds.get(0).getStatus()).isZero();
        assertThat(beds.get(0).getWardName()).isEqualTo("内科一病区");
    }
}
