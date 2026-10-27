package com.hospital.inpatient.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.security.LoginUser;
import com.hospital.inpatient.dto.OrderRequest;
import com.hospital.inpatient.dto.OrderVO;
import com.hospital.inpatient.entity.BizAdmission;
import com.hospital.inpatient.entity.BizMedicalOrder;
import com.hospital.inpatient.mapper.BizAdmissionMapper;
import com.hospital.inpatient.mapper.BizMedicalOrderMapper;
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

import java.time.LocalDateTime;
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
 * 住院医嘱：开立、执行、停止、查询。
 *
 * <p>两条硬规则：只有<b>在院</b>患者能开医嘱（否则出院结算后还会冒出费用）；
 * 开嘱医生<b>取当前登录用户</b>，不接受前端传值（医嘱是法律文书，必须可追溯到人）。</p>
 */
@ExtendWith(MockitoExtension.class)
class MedicalOrderServiceTest {

    private static final Long ADMISSION_ID = 7001L;
    private static final Long ORDER_ID = 7101L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long OTHER_DOCTOR_ID = 4L;
    private static final Long OPERATOR_ID = 8L;

    @Mock
    private BizMedicalOrderMapper orderMapper;
    @Mock
    private BizAdmissionMapper admissionMapper;
    @Mock
    private SysUserMapper userMapper;

    private MedicalOrderService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizMedicalOrder.class);
        TableInfoHelper.initTableInfo(assistant, BizAdmission.class);
    }

    @BeforeEach
    void setUp() {
        service = new MedicalOrderService(orderMapper, admissionMapper, userMapper);
        SecurityContextHolder.clearContext();
        lenient().doAnswer(inv -> {
            BizMedicalOrder entity = inv.getArgument(0);
            entity.setId(ORDER_ID);
            return 1;
        }).when(orderMapper).insert(any(BizMedicalOrder.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String... roles) {
        LoginUser user = LoginUser.builder()
                .userId(userId)
                .username("doctor")
                .roleCodes(new LinkedHashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private BizAdmission admission(Integer status) {
        BizAdmission admission = new BizAdmission();
        admission.setId(ADMISSION_ID);
        admission.setPatientId(100L);
        admission.setStatus(status);
        return admission;
    }

    private BizMedicalOrder order(Integer status) {
        BizMedicalOrder order = new BizMedicalOrder();
        order.setId(ORDER_ID);
        order.setAdmissionId(ADMISSION_ID);
        order.setOrderType(1);
        order.setContent("头孢曲松 2g 静滴");
        order.setDoctorId(DOCTOR_ID);
        order.setStatus(status);
        return order;
    }

    private OrderRequest request(Integer orderType) {
        OrderRequest request = new OrderRequest();
        request.setAdmissionId(ADMISSION_ID);
        request.setOrderType(orderType);
        request.setContent("头孢曲松 2g 静滴");
        request.setDrugId(11L);
        request.setDosage("2g");
        request.setFrequency("qd");
        return request;
    }

    /* ---------------- 开立 ---------------- */

    @Test
    @DisplayName("住院记录不存在时报 40400")
    void admissionNotFound() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(null);
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.create(request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("住院记录不存在");

        verify(orderMapper, never()).insert(any(BizMedicalOrder.class));
    }

    @Test
    @DisplayName("已出院患者不能再开立医嘱（40902）")
    void dischargedPatientCannotCreateOrder() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(1));
        loginAs(DOCTOR_ID, "doctor");

        assertThatThrownBy(() -> service.create(request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已出院");

        verify(orderMapper, never()).insert(any(BizMedicalOrder.class));
    }

    @Test
    @DisplayName("开立成功：医生取当前登录用户，不接受前端传值")
    void createUsesCurrentUserAsDoctor() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));
        loginAs(DOCTOR_ID, "doctor");

        Long id = service.create(request(1));

        assertThat(id).isEqualTo(ORDER_ID);
        ArgumentCaptor<BizMedicalOrder> captor = ArgumentCaptor.forClass(BizMedicalOrder.class);
        verify(orderMapper).insert(captor.capture());
        BizMedicalOrder saved = captor.getValue();
        assertThat(saved.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getOrderType()).isEqualTo(1);
        assertThat(saved.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("开嘱医生不受前端 doctorId 影响（请求体里根本没有该字段）")
    void doctorIdCannotBeSpoofed() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));
        loginAs(OTHER_DOCTOR_ID, "doctor");

        service.create(request(2));

        ArgumentCaptor<BizMedicalOrder> captor = ArgumentCaptor.forClass(BizMedicalOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getDoctorId()).isEqualTo(OTHER_DOCTOR_ID);
        assertThat(captor.getValue().getOrderType()).isEqualTo(2);
    }

    @Test
    @DisplayName("未登录开医嘱直接 40101")
    void createRequiresLogin() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));

        assertThatThrownBy(() -> service.create(request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录状态已失效");

        verify(orderMapper, never()).insert(any(BizMedicalOrder.class));
    }

    @Test
    @DisplayName("长期与临时医嘱都可开立，状态一律从未执行开始")
    void bothOrderTypesStartAsPending() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));
        loginAs(DOCTOR_ID, "doctor");

        service.create(request(1));
        service.create(request(2));

        ArgumentCaptor<BizMedicalOrder> captor = ArgumentCaptor.forClass(BizMedicalOrder.class);
        verify(orderMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(o -> assertThat(o.getStatus()).isZero());
        assertThat(captor.getAllValues().get(0).getOrderType()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).getOrderType()).isEqualTo(2);
    }

    /* ---------------- 执行 ---------------- */

    @Test
    @DisplayName("执行医嘱：未执行 → 已执行")
    void executeSucceeds() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(0));
        when(orderMapper.execute(ORDER_ID, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "nurse");

        service.execute(ORDER_ID);

        verify(orderMapper).execute(ORDER_ID, OPERATOR_ID);
    }

    @Test
    @DisplayName("已执行或已停止的医嘱不能重复执行（40902）")
    void executeWrongStatus() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(1));
        when(orderMapper.execute(anyLong(), any())).thenReturn(0);
        loginAs(OPERATOR_ID, "nurse");

        assertThatThrownBy(() -> service.execute(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有未执行的医嘱可以执行");
    }

    @Test
    @DisplayName("医嘱不存在时报 40400")
    void executeOrderNotFound() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.execute(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("医嘱不存在");

        verify(orderMapper, never()).execute(anyLong(), any());
    }

    /* ---------------- 停止 ---------------- */

    @Test
    @DisplayName("停止医嘱成功")
    void stopSucceeds() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(0));
        when(orderMapper.stop(ORDER_ID, OPERATOR_ID)).thenReturn(1);
        loginAs(OPERATOR_ID, "nurse");

        service.stop(ORDER_ID);

        verify(orderMapper).stop(ORDER_ID, OPERATOR_ID);
    }

    @Test
    @DisplayName("已停止的医嘱不能再次停止（40902）")
    void stopAlreadyStopped() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(2));

        assertThatThrownBy(() -> service.stop(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该医嘱已停止");

        verify(orderMapper, never()).stop(anyLong(), any());
    }

    @Test
    @DisplayName("并发停止导致状态变化时抛 40900（带原状态条件，不会刷新停止时间）")
    void stopConflict() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(0));
        when(orderMapper.stop(anyLong(), any())).thenReturn(0);
        loginAs(OPERATOR_ID, "nurse");

        assertThatThrownBy(() -> service.stop(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("医嘱状态已变化");
    }

    @Test
    @DisplayName("停止不存在的医嘱报 40400")
    void stopOrderNotFound() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.stop(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("医嘱不存在");
    }

    /* ---------------- 查询 ---------------- */

    @Test
    @DisplayName("无医嘱时返回空列表而不是 null")
    void listByAdmissionEmpty() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        List<OrderVO> list = service.listByAdmission(ADMISSION_ID);

        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("医嘱列表带出开嘱医生姓名")
    void listByAdmissionResolvesDoctorName() {
        BizMedicalOrder o1 = order(0);
        BizMedicalOrder o2 = order(2);
        o2.setId(7102L);
        when(orderMapper.selectList(any())).thenReturn(List.of(o1, o2));
        SysUser doctor = new SysUser();
        doctor.setId(DOCTOR_ID);
        doctor.setRealName("王医生");
        when(userMapper.selectByIds(any())).thenReturn(List.of(doctor));

        List<OrderVO> list = service.listByAdmission(ADMISSION_ID);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getDoctorName()).isEqualTo("王医生");
        assertThat(list.get(0).getContent()).isEqualTo("头孢曲松 2g 静滴");
        assertThat(list.get(1).getStatus()).isEqualTo(2);
        // 医嘱列表按开立时间倒序，最新的在前
        assertThat(list.get(0).getId()).isEqualTo("7101");
    }

    @Test
    @DisplayName("医嘱开始时间未传时取当前时间")
    void startTimeDefaultsToNow() {
        when(admissionMapper.selectById(ADMISSION_ID)).thenReturn(admission(0));
        loginAs(DOCTOR_ID, "doctor");
        LocalDateTime before = LocalDateTime.now().minusSeconds(2);

        service.create(request(1));

        ArgumentCaptor<BizMedicalOrder> captor = ArgumentCaptor.forClass(BizMedicalOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStartTime()).isAfter(before);
    }
}
