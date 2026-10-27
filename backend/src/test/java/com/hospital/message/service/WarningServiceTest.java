package com.hospital.message.service;

import com.hospital.message.dto.WarningRow;
import com.hospital.message.dto.WarningVO;
import com.hospital.message.mapper.WarningMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预警中心。
 *
 * <p>要锁死两件事：<b>分档</b>（穿仓/已过期/危急值必须是紧急）
 * 与<b>单源失败不影响其它源</b>（这是看板，少一块可以，整个打不开不行）。</p>
 */
@ExtendWith(MockitoExtension.class)
class WarningServiceTest {

    @Mock
    private WarningMapper warningMapper;

    private WarningService service;

    @BeforeEach
    void setUp() {
        service = new WarningService(warningMapper);
    }

    @Test
    @DisplayName("库存穿仓（余量 0）判为紧急")
    void stockOutOfLevel() {
        when(warningMapper.selectStockWarn(20))
                .thenReturn(List.of(row(null, "布洛芬", null, "批号 B001", 0, 10, null, null)));

        List<WarningVO> list = service.list(0);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getLevel()).isEqualTo(WarningService.LEVEL_URGENT);
        assertThat(list.get(0).getBizType()).isEqualTo("stock_warn");
        assertThat(list.get(0).getTitle()).contains("布洛芬");
    }

    @Test
    @DisplayName("库存余量高于预警值一半只算提示")
    void stockInfoLevel() {
        when(warningMapper.selectStockWarn(20))
                .thenReturn(List.of(row(null, "阿莫西林", null, "批号 A001", 9, 10, null, null)));

        assertThat(service.list(0).get(0).getLevel()).isEqualTo(WarningService.LEVEL_INFO);
    }

    @Test
    @DisplayName("已过效期判为紧急，未过期只算提示")
    void expireLevel() {
        when(warningMapper.selectDrugExpire(anyInt(), anyInt())).thenReturn(List.of(
                row(null, "甲药", null, "批号 X", -3, null, LocalDate.now().minusDays(3), -3),
                row(null, "乙药", null, "批号 Y", 80, null, LocalDate.now().plusDays(80), 80)));

        List<WarningVO> list = service.list(0);

        assertThat(list).hasSize(2);
        // 紧急排前面
        assertThat(list.get(0).getLevel()).isEqualTo(WarningService.LEVEL_URGENT);
        assertThat(list.get(1).getLevel()).isEqualTo(WarningService.LEVEL_INFO);
    }

    @Test
    @DisplayName("危急值一律紧急，且患者姓名脱敏")
    void criticalMasked() {
        when(warningMapper.selectCritical(20))
                .thenReturn(List.of(row(1L, "血钾", "张三", "JC20261027001", null, null, LocalDate.now(), null)));

        WarningVO vo = service.list(0).get(0);

        assertThat(vo.getLevel()).isEqualTo(WarningService.LEVEL_URGENT);
        // 患者姓名在 title 里，且必须脱敏
        assertThat(vo.getTitle()).contains("张*").doesNotContain("张三");
    }

    @Test
    @DisplayName("传染病超期未上报判为紧急")
    void infectiousOverdue() {
        when(warningMapper.selectInfectiousOverdue(20)).thenReturn(List.of(
                row(1L, "手足口病", "李四", "未编码", 2, null, LocalDate.now().minusDays(2), 2)));

        WarningVO vo = service.list(0).get(0);

        assertThat(vo.getLevel()).isEqualTo(WarningService.LEVEL_URGENT);
        assertThat(vo.getBizTypeName()).isEqualTo("传染病超期");
        assertThat(vo.getContent()).contains("已逾期 2 天");
    }

    @Test
    @DisplayName("疫苗应种日已到判为警告，未到只算提示")
    void vaccineLevel() {
        when(warningMapper.selectVaccineOverdue(anyInt(), anyInt())).thenReturn(List.of(
                row(1L, "乙肝疫苗", "王五", "第 2 剂", 5, null, LocalDate.now().minusDays(5), 5),
                row(2L, "流感疫苗", "赵六", "第 1 剂", -10, null, LocalDate.now().plusDays(10), -10)));

        List<WarningVO> list = service.list(0);

        assertThat(list.get(0).getLevel()).isEqualTo(WarningService.LEVEL_WARN);
        assertThat(list.get(1).getLevel()).isEqualTo(WarningService.LEVEL_INFO);
        assertThat(list.get(1).getContent()).contains("还有 10 天");
    }

    @Test
    @DisplayName("单个数据源异常不影响其它源（看板宁可少一块，不能整个打不开）")
    void oneSourceFailureIsolated() {
        when(warningMapper.selectStockWarn(20)).thenThrow(new RuntimeException("表被锁了"));
        when(warningMapper.selectCritical(20)).thenReturn(List.of(
                row(1L, "血钾", "张三", "JC001", null, null, LocalDate.now(), null)));

        List<WarningVO> list = service.list(0);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getBizType()).isEqualTo("critical");
    }

    @Test
    @DisplayName("limit 超过 100 按 100 封顶")
    void limitUpperBound() {
        service.list(500);

        verify(warningMapper).selectStockWarn(100);
    }

    @Test
    @DisplayName("limit <= 0 时取默认 20")
    void limitDefault() {
        service.list(-1);

        verify(warningMapper).selectStockWarn(20);
    }

    @Test
    @DisplayName("五类预警齐备时按紧急度降序，同级别按业务类型分组")
    void sortedByLevel() {
        when(warningMapper.selectStockWarn(20)).thenReturn(List.of(
                row(null, "阿莫西林", null, "批号 A", 9, 10, null, null)));
        when(warningMapper.selectCritical(20)).thenReturn(List.of(
                row(1L, "血钾", "张三", "JC001", null, null, LocalDate.now(), null)));
        when(warningMapper.selectVaccineOverdue(anyInt(), anyInt())).thenReturn(List.of(
                row(2L, "乙肝疫苗", "王五", "第 2 剂", 5, null, LocalDate.now().minusDays(5), 5)));

        List<WarningVO> list = service.list(0);

        assertThat(list).extracting(WarningVO::getLevel)
                .containsExactly(WarningService.LEVEL_URGENT, WarningService.LEVEL_WARN,
                        WarningService.LEVEL_INFO);
    }

    /* ---------------- 辅助 ---------------- */

    private WarningRow row(Long bizId, String subject, String patientName, String detail,
                           Integer value, Integer warnValue, LocalDate dueDate, Integer days) {
        return new WarningRow(
                bizId,
                subject,
                patientName,
                detail,
                value == null ? null : BigDecimal.valueOf(value),
                warnValue == null ? null : BigDecimal.valueOf(warnValue),
                dueDate,
                days);
    }
}
