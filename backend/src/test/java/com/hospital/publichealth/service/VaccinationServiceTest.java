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
import com.hospital.publichealth.dto.VaccinationQuery;
import com.hospital.publichealth.dto.VaccinationRequest;
import com.hospital.publichealth.dto.VaccinationVO;
import com.hospital.publichealth.entity.BizVaccination;
import com.hospital.publichealth.mapper.BizVaccinationMapper;
import com.hospital.system.entity.SysUser;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 疫苗接种登记。
 *
 * <p>三条硬规则必须锁死：批号真实存在、剂次不重复、接种日期不能是未来。
 * 任一条失守的后果都不是数据难看 —— 批号写错追溯不到人，剂次重复会催着补打一针。</p>
 */
@ExtendWith(MockitoExtension.class)
class VaccinationServiceTest {

    private static final Long PATIENT_ID = 100L;
    private static final Long RECORD_ID = 6101L;
    private static final Long OPERATOR_ID = 8L;
    private static final String VACCINE = "流感疫苗（四价）";
    private static final String BATCH = "FLU20261001";

    @Mock
    private BizVaccinationMapper vaccinationMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;

    private VaccinationService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizVaccination.class);
        TableInfoHelper.initTableInfo(assistant, BizPatient.class);
    }

    @BeforeEach
    void setUp() {
        service = new VaccinationService(vaccinationMapper, patientMapper, userMapper);
        loginAs(OPERATOR_ID);
        lenient().doAnswer(inv -> {
            BizVaccination entity = inv.getArgument(0);
            entity.setId(RECORD_ID);
            return 1;
        }).when(vaccinationMapper).insert(any(BizVaccination.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("患者不存在时拒绝登记（40400）")
    void patientNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertCode(() -> service.add(request(null)), ErrorCode.NO_RESOURCE);
        verify(vaccinationMapper, never()).insert(any(BizVaccination.class));
    }

    @Test
    @DisplayName("接种日期不能是未来（40001）")
    void futureDateRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());

        VaccinationRequest request = request(1);
        request.setVaccinateDate(LocalDate.now().plusDays(1));

        assertCode(() -> service.add(request), ErrorCode.PARAM_INVALID);
        verify(vaccinationMapper, never()).insert(any(BizVaccination.class));
    }

    @Test
    @DisplayName("批号必须是库存里真实存在的疫苗批号（40001）")
    void unknownBatchRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(vaccinationMapper.countVaccineBatch(BATCH)).thenReturn(0);

        assertCode(() -> service.add(request(1)), ErrorCode.PARAM_INVALID);
        verify(vaccinationMapper, never()).insert(any(BizVaccination.class));
    }

    @Test
    @DisplayName("不传剂次时按历史最大剂次 +1 推算")
    void doseNoAutoIncrements() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(vaccinationMapper.countVaccineBatch(BATCH)).thenReturn(1);
        when(vaccinationMapper.selectMaxDoseNo(PATIENT_ID, VACCINE)).thenReturn(2);
        when(vaccinationMapper.countSameDose(PATIENT_ID, VACCINE, 3)).thenReturn(0);

        service.add(request(null));

        ArgumentCaptor<BizVaccination> captor = ArgumentCaptor.forClass(BizVaccination.class);
        verify(vaccinationMapper).insert(captor.capture());
        assertThat(captor.getValue().getDoseNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("同一剂次重复登记被拒（40904）")
    void duplicateDoseRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(vaccinationMapper.countVaccineBatch(BATCH)).thenReturn(1);
        when(vaccinationMapper.countSameDose(PATIENT_ID, VACCINE, 2)).thenReturn(1);

        assertCode(() -> service.add(request(2)), ErrorCode.DUPLICATE_DATA);
        verify(vaccinationMapper, never()).insert(any(BizVaccination.class));
    }

    @Test
    @DisplayName("剂次必须大于 0（40001）")
    void nonPositiveDoseRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(vaccinationMapper.countVaccineBatch(BATCH)).thenReturn(1);

        assertCode(() -> service.add(request(0)), ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("登记成功：接种人取当前登录用户，不接受前端传值")
    void addSuccess() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(vaccinationMapper.countVaccineBatch(BATCH)).thenReturn(1);
        when(vaccinationMapper.countSameDose(PATIENT_ID, VACCINE, 1)).thenReturn(0);

        Long id = service.add(request(1));

        assertThat(id).isEqualTo(RECORD_ID);
        ArgumentCaptor<BizVaccination> captor = ArgumentCaptor.forClass(BizVaccination.class);
        verify(vaccinationMapper).insert(captor.capture());
        BizVaccination saved = captor.getValue();
        assertThat(saved.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getBatchNo()).isEqualTo(BATCH);
        assertThat(saved.getVaccineName()).isEqualTo(VACCINE);
    }

    @Test
    @DisplayName("未登录时拒绝登记（40101）—— 接种人必须可追溯")
    void notLoggedIn() {
        SecurityContextHolder.clearContext();

        assertCode(() -> service.add(request(1)), ErrorCode.UNAUTHORIZED);
        verify(patientMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("接种提醒默认前瞻 30 天")
    void remindDefaultDays() {
        when(vaccinationMapper.selectRemind(anyInt(), anyInt())).thenReturn(List.of(entity(null)));
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(operator()));

        List<VaccinationVO> list = service.remind(null);

        verify(vaccinationMapper).selectRemind(eq(30), anyInt());
        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("提醒天数超过 365 天时收敛到上限，防止全表扫")
    void remindDaysCapped() {
        when(vaccinationMapper.selectRemind(anyInt(), anyInt())).thenReturn(List.of());

        assertThat(service.remind(9999)).isEmpty();
        verify(vaccinationMapper).selectRemind(eq(365), anyInt());
    }

    @Test
    @DisplayName("已逾期的接种在提醒里标记 overdue")
    void remindMarksOverdue() {
        when(vaccinationMapper.selectRemind(anyInt(), anyInt()))
                .thenReturn(List.of(entity(LocalDate.now().minusDays(5))));
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(operator()));

        List<VaccinationVO> list = service.remind(30);

        assertThat(list.get(0).getOverdue()).isTrue();
    }

    @Test
    @DisplayName("分页补齐患者与接种人姓名（不逐条查库）")
    void pageResolvesNames() {
        Page<BizVaccination> page = new Page<>(1, 10);
        page.setRecords(List.of(entity(LocalDate.now().plusDays(10))));
        page.setTotal(1);
        when(vaccinationMapper.selectPage(any(), any())).thenReturn(page);
        when(patientMapper.selectByIds(any())).thenReturn(List.of(patient()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(operator()));

        PageResult<VaccinationVO> result = service.page(new VaccinationQuery());

        VaccinationVO vo = result.getList().get(0);
        assertThat(vo.getPatientName()).isEqualTo("郑新患");
        assertThat(vo.getOperatorName()).isEqualTo("李护士");
        assertThat(vo.getOverdue()).isFalse();
        // 姓名只查一次，不是每条记录查一次
        verify(patientMapper).selectByIds(any());
    }

    /* ---------------- 辅助 ---------------- */

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("nurse01").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizPatient patient() {
        BizPatient patient = new BizPatient();
        patient.setId(PATIENT_ID);
        patient.setName("郑新患");
        return patient;
    }

    private SysUser operator() {
        SysUser user = new SysUser();
        user.setId(OPERATOR_ID);
        user.setRealName("李护士");
        return user;
    }

    private BizVaccination entity(LocalDate nextDate) {
        BizVaccination entity = new BizVaccination();
        entity.setId(RECORD_ID);
        entity.setPatientId(PATIENT_ID);
        entity.setVaccineName(VACCINE);
        entity.setBatchNo(BATCH);
        entity.setDoseNo(1);
        entity.setVaccinateDate(LocalDate.now());
        entity.setOperatorId(OPERATOR_ID);
        entity.setNextDate(nextDate);
        return entity;
    }

    private VaccinationRequest request(Integer doseNo) {
        VaccinationRequest request = new VaccinationRequest();
        request.setPatientId(PATIENT_ID);
        request.setVaccineName(VACCINE);
        request.setBatchNo(BATCH);
        request.setDoseNo(doseNo);
        request.setVaccinateDate(LocalDate.now());
        return request;
    }
}
