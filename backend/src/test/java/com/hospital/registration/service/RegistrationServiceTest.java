package com.hospital.registration.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.entity.BizPatient;
import com.hospital.patient.mapper.BizPatientMapper;
import com.hospital.registration.dto.RegistrationRequest;
import com.hospital.registration.dto.RegistrationResult;
import com.hospital.registration.entity.BizRegistration;
import com.hospital.registration.entity.BizSchedule;
import com.hospital.registration.mapper.BizRegistrationMapper;
import com.hospital.registration.mapper.BizScheduleMapper;
import com.hospital.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 挂号与退号：号源扣减、重复挂号拦截、退号幂等。
 *
 * <p>号源是本模块最贵的东西：扣多了医院超负荷，扣少了收入流失。
 * 所以这里既验「扣减失败要报 40901」，也验「重复退号不能重复回补」。</p>
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Long PATIENT_ID = 100L;
    private static final Long SCHEDULE_ID = 200L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long DEPT_ID = 1001L;

    @Mock
    private BizRegistrationMapper registrationMapper;
    @Mock
    private BizScheduleMapper scheduleMapper;
    @Mock
    private BizPatientMapper patientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private RegNoGenerator regNoGenerator;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RegistrationService service;

    /**
     * 纯单测没有 MyBatis 上下文，{@code Wrappers.lambdaUpdate()} 会因缺少
     * TableInfo 缓存而抛 {@code can not find lambda cache}。这里手动初始化一次。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), BizRegistration.class);
    }

    @BeforeEach
    void setUp() {
        service = new RegistrationService(registrationMapper, scheduleMapper, patientMapper,
                userMapper, regNoGenerator, redis);
        // register 成功后要用 id 组装返回值；失败用例走不到 insert，故宽松处理
        lenient().doAnswer(inv -> {
            BizRegistration entity = inv.getArgument(0);
            entity.setId(9001L);
            return 1;
        }).when(registrationMapper).insert(any(BizRegistration.class));
    }

    private BizPatient patient() {
        BizPatient entity = new BizPatient();
        entity.setId(PATIENT_ID);
        entity.setName("张三");
        entity.setPatientNo("P0001");
        return entity;
    }

    private BizSchedule schedule(int status, LocalDate date) {
        BizSchedule entity = new BizSchedule();
        entity.setId(SCHEDULE_ID);
        entity.setDoctorId(DOCTOR_ID);
        entity.setDeptId(DEPT_ID);
        entity.setScheduleDate(date);
        entity.setRegType(2);
        entity.setRegFee(new BigDecimal("80.00"));
        entity.setTotalNum(10);
        entity.setRemainNum(5);
        entity.setStatus(status);
        return entity;
    }

    private RegistrationRequest request() {
        RegistrationRequest request = new RegistrationRequest();
        request.setPatientId(PATIENT_ID);
        request.setScheduleId(SCHEDULE_ID);
        return request;
    }

    private void stubQueueSeq(long seq) {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.increment(anyString())).thenReturn(seq);
        lenient().when(valueOps.increment(anyString(), anyLong())).thenReturn(seq + 1);
        lenient().when(registrationMapper.selectMaxQueueNo(anyLong())).thenReturn(0);
        lenient().when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    @DisplayName("患者不存在时提示先建档")
    void patientNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先建档");

        verify(registrationMapper, never()).insert(any(BizRegistration.class));
    }

    @Test
    @DisplayName("排班不存在时拒绝挂号")
    void scheduleNotFound() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.register(request()))
                .hasMessageContaining("排班不存在");
    }

    @Test
    @DisplayName("停诊的排班不能挂号")
    void stoppedScheduleIsRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(schedule(0, LocalDate.now().plusDays(1)));

        assertThatThrownBy(() -> service.register(request()))
                .hasMessageContaining("已停诊");
    }

    @Test
    @DisplayName("过期排班不能挂号（昨天的号今天不能挂）")
    void pastScheduleIsRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(schedule(1, LocalDate.now().minusDays(1)));

        assertThatThrownBy(() -> service.register(request()))
                .hasMessageContaining("日期已过");
    }

    @Test
    @DisplayName("同一患者同一排班不能重复挂号（防占号）")
    void duplicateRegistrationIsRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(schedule(1, LocalDate.now().plusDays(1)));
        when(registrationMapper.countActiveByScheduleAndPatient(SCHEDULE_ID, PATIENT_ID)).thenReturn(1);

        assertThatThrownBy(() -> service.register(request()))
                .hasMessageContaining("已有有效挂号");

        // 关键：重复挂号不能扣号源
        verify(scheduleMapper, never()).decreaseStock(anyLong());
    }

    @Test
    @DisplayName("号源扣减失败（已抢完）返回 40901 且不落单")
    void noStockIsRejected() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(schedule(1, LocalDate.now().plusDays(1)));
        when(registrationMapper.countActiveByScheduleAndPatient(SCHEDULE_ID, PATIENT_ID)).thenReturn(0);
        when(scheduleMapper.decreaseStock(SCHEDULE_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("号源不足");

        verify(registrationMapper, never()).insert(any(BizRegistration.class));
    }

    @Test
    @DisplayName("挂号成功：单号、排队号、金额与医生科室均取自服务端")
    void registerSucceeds() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(schedule(1, LocalDate.now().plusDays(1)));
        when(registrationMapper.countActiveByScheduleAndPatient(SCHEDULE_ID, PATIENT_ID)).thenReturn(0);
        when(scheduleMapper.decreaseStock(SCHEDULE_ID)).thenReturn(1);
        when(regNoGenerator.next()).thenReturn("REG202610260001");
        stubQueueSeq(1L);

        RegistrationResult result = service.register(request());

        assertThat(result.getRegNo()).isEqualTo("REG202610260001");
        assertThat(result.getQueueNo()).isEqualTo(1);
        assertThat(result.getRegFee()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(result.getStatus()).isZero();

        ArgumentCaptor<BizRegistration> captor = ArgumentCaptor.forClass(BizRegistration.class);
        verify(registrationMapper).insert(captor.capture());
        BizRegistration saved = captor.getValue();
        // 号别、费用、医生、科室都取排班，前端传值一概不用
        assertThat(saved.getRegType()).isEqualTo(2);
        assertThat(saved.getRegFee()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(saved.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(saved.getDeptId()).isEqualTo(DEPT_ID);
        assertThat(saved.getStatus()).isZero();
    }

    @Test
    @DisplayName("退号：状态置已退并回补号源")
    void cancelRestoresStock() {
        BizRegistration registration = new BizRegistration();
        registration.setId(9001L);
        registration.setScheduleId(SCHEDULE_ID);
        registration.setStatus(0);
        registration.setRegNo("REG202610260001");
        when(registrationMapper.selectById(9001L)).thenReturn(registration);
        when(registrationMapper.update(any(), any())).thenReturn(1);

        service.cancel(9001L);

        verify(scheduleMapper).increaseStock(SCHEDULE_ID);
    }

    @Test
    @DisplayName("重复退号被拒绝，且不会重复回补号源")
    void cancelTwiceIsRejected() {
        BizRegistration registration = new BizRegistration();
        registration.setId(9001L);
        registration.setStatus(3);
        when(registrationMapper.selectById(9001L)).thenReturn(registration);

        assertThatThrownBy(() -> service.cancel(9001L))
                .hasMessageContaining("已退号");

        verify(scheduleMapper, never()).increaseStock(anyLong());
    }

    @Test
    @DisplayName("已完成的挂号不能退号")
    void cancelFinishedIsRejected() {
        BizRegistration registration = new BizRegistration();
        registration.setId(9001L);
        registration.setStatus(2);
        when(registrationMapper.selectById(9001L)).thenReturn(registration);

        assertThatThrownBy(() -> service.cancel(9001L))
                .hasMessageContaining("已完成");

        verify(scheduleMapper, never()).increaseStock(anyLong());
    }

    @Test
    @DisplayName("就诊中的挂号需医生先结束接诊才能退")
    void cancelInVisitIsRejected() {
        BizRegistration registration = new BizRegistration();
        registration.setId(9001L);
        registration.setStatus(1);
        when(registrationMapper.selectById(9001L)).thenReturn(registration);

        assertThatThrownBy(() -> service.cancel(9001L))
                .hasMessageContaining("就诊中");
    }

    @Test
    @DisplayName("并发退号时只有一个能成功，另一个报冲突且不补号源")
    void concurrentCancelOnlyOneSucceeds() {
        BizRegistration registration = new BizRegistration();
        registration.setId(9001L);
        registration.setScheduleId(SCHEDULE_ID);
        registration.setStatus(0);
        when(registrationMapper.selectById(9001L)).thenReturn(registration);
        // 条件更新影响 0 行 = 被别人先退了
        when(registrationMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(9001L))
                .hasMessageContaining("状态已变化");

        verify(scheduleMapper, never()).increaseStock(anyLong());
    }

    @Test
    @DisplayName("叫号：待就诊 → 就诊中")
    void callMovesToInVisit() {
        when(registrationMapper.update(any(), any())).thenReturn(1);

        service.call(9001L);

        verify(registrationMapper).update(any(), any());
    }

    @Test
    @DisplayName("非待就诊的挂号不能叫号")
    void callNonWaitingIsRejected() {
        when(registrationMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.call(9001L))
                .hasMessageContaining("只有待就诊");
    }

    @Test
    @DisplayName("排班日期必须是今天或之后，昨天以前一律拒绝")
    void scheduleDateBoundary() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(patient());
        // 今天仍然可以挂号
        when(scheduleMapper.selectById(SCHEDULE_ID)).thenReturn(schedule(1, LocalDate.now()));
        when(registrationMapper.countActiveByScheduleAndPatient(SCHEDULE_ID, PATIENT_ID)).thenReturn(0);
        when(scheduleMapper.decreaseStock(SCHEDULE_ID)).thenReturn(1);
        when(regNoGenerator.next()).thenReturn("REG202610260002");
        stubQueueSeq(3L);

        RegistrationResult result = service.register(request());

        assertThat(result.getRegNo()).isEqualTo("REG202610260002");
        verify(scheduleMapper).decreaseStock(SCHEDULE_ID);
    }

    @Test
    @DisplayName("患者不存在时不会去扣号源")
    void noStockTouchWhenPatientInvalid() {
        when(patientMapper.selectById(PATIENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(BusinessException.class);

        verify(scheduleMapper, never()).decreaseStock(eq(SCHEDULE_ID));
    }
}
