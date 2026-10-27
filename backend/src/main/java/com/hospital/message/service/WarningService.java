package com.hospital.message.service;

import com.hospital.common.util.MaskUtil;
import com.hospital.message.dto.WarningRow;
import com.hospital.message.dto.WarningVO;
import com.hospital.message.mapper.WarningMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * 预警中心：把散落在各模块的异常聚成一张清单。
 *
 * <p><b>不落库</b>：落库就成了快照。库存补上了预警还在、传染病上报了还挂着，
 * 用的人看两次发现不准，第三次就不会再点开这个页面 ——
 * 预警的价值全在「现在这一刻确实有问题」上。所以每次打开实时算。</p>
 *
 * <p><b>单个数据源失败要吞掉</b>：这是给运营看的一块看板，
 * 效期表查不出来不该让危急值也跟着看不见。每个源单独 try-catch，
 * 失败只记 error 日志并跳过该源。</p>
 */
@Slf4j
@Service
public class WarningService {

    /** 1 提示 / 2 警告 / 3 紧急 */
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_URGENT = 3;

    /** 近效期提前天数，与系统参数 sys.drug.expire.warn.days=90 对齐 */
    private static final int EXPIRE_AHEAD_DAYS = 90;
    /** 疫苗到期提前天数 */
    private static final int VACCINE_AHEAD_DAYS = 15;
    /** 单类预警最多取多少条，防止一张表把列表撑爆 */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final WarningMapper warningMapper;

    public WarningService(WarningMapper warningMapper) {
        this.warningMapper = warningMapper;
    }

    /**
     * 聚合全部预警，按紧急度降序。
     *
     * @param limit 每类最多条数；<=0 时取默认 20，上限 100
     */
    public List<WarningVO> list(int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<WarningVO> result = new ArrayList<>();
        result.addAll(safe(() -> toStockWarn(warningMapper.selectStockWarn(size)), "库存预警"));
        result.addAll(safe(() -> toDrugExpire(warningMapper.selectDrugExpire(EXPIRE_AHEAD_DAYS, size)), "效期预警"));
        result.addAll(safe(() -> toCritical(warningMapper.selectCritical(size)), "危急值"));
        result.addAll(safe(() -> toInfectious(warningMapper.selectInfectiousOverdue(size)), "传染病超期"));
        result.addAll(safe(() -> toVaccine(warningMapper.selectVaccineOverdue(VACCINE_AHEAD_DAYS, size)), "疫苗逾期"));

        // 紧急的排前面；同级别按业务类型分组，免得五类预警交错看着乱
        result.sort(Comparator.comparing(WarningVO::getLevel, Comparator.reverseOrder())
                .thenComparing(WarningVO::getBizType));
        return result;
    }

    /** 各类预警的条数，给首页角标用 */
    public int count() {
        return list(DEFAULT_LIMIT).size();
    }

    /* ---------------- 各源转换 ---------------- */

    private List<WarningVO> toStockWarn(List<WarningRow> rows) {
        return rows.stream().map(r -> {
            int quantity = intValue(r.getValue());
            int warnNum = intValue(r.getWarnValue());
            // 已穿仓最急；余量不到预警值一半次之
            int level = quantity <= 0 ? LEVEL_URGENT
                    : (quantity * 2 <= warnNum ? LEVEL_WARN : LEVEL_INFO);
            String title = r.getSubject() + " 库存不足";
            String content = "当前库存 " + quantity + "，预警值 " + warnNum
                    + "（" + r.getDetail() + "）";
            return base("stock_warn", "库存预警", r.getBizId(), title, content, level,
                    BigDecimal.valueOf(quantity), r.getDueDate());
        }).toList();
    }

    private List<WarningVO> toDrugExpire(List<WarningRow> rows) {
        return rows.stream().map(r -> {
            int days = r.getDays() == null ? 0 : r.getDays();
            int level = days < 0 ? LEVEL_URGENT : (days <= 30 ? LEVEL_WARN : LEVEL_INFO);
            String title = r.getSubject() + (days < 0 ? " 已过效期" : " 临近效期");
            String content = r.getDetail() + "，有效期至 " + r.getDueDate()
                    + (days < 0 ? "，已过期 " + (-days) + " 天" : "，剩余 " + days + " 天");
            return base("drug_expire", "效期预警", r.getBizId(), title, content, level,
                    BigDecimal.valueOf(days), r.getDueDate());
        }).toList();
    }

    private List<WarningVO> toCritical(List<WarningRow> rows) {
        return rows.stream().map(r -> {
            String title = "危急值：" + MaskUtil.name(r.getPatientName()) + " " + r.getSubject();
            String content = "报告 " + r.getDetail() + " 检出危急值，请立即联系临床处理";
            return base("critical", "危急值", r.getBizId(), title, content, LEVEL_URGENT,
                    null, r.getDueDate());
        }).toList();
    }

    private List<WarningVO> toInfectious(List<WarningRow> rows) {
        return rows.stream().map(r -> {
            int days = r.getDays() == null ? 0 : r.getDays();
            String title = "传染病报告卡超期未上报：" + r.getSubject();
            String content = MaskUtil.name(r.getPatientName()) + "（" + r.getDetail() + "）于 "
                    + r.getDueDate() + " 填卡，已逾期 " + days + " 天";
            return base("infectious_overdue", "传染病超期", r.getBizId(), title, content, LEVEL_URGENT,
                    BigDecimal.valueOf(days), r.getDueDate());
        }).toList();
    }

    private List<WarningVO> toVaccine(List<WarningRow> rows) {
        return rows.stream().map(r -> {
            int days = r.getDays() == null ? 0 : r.getDays();
            // days>=0 表示应种日已到或已过；<0 表示提前提醒
            int level = days >= 0 ? LEVEL_WARN : LEVEL_INFO;
            String title = r.getSubject() + " " + r.getDetail() + (days >= 0 ? " 逾期未种" : " 待接种");
            String content = MaskUtil.name(r.getPatientName()) + "，应种日期 " + r.getDueDate()
                    + (days >= 0 ? "，已逾期 " + days + " 天" : "，还有 " + (-days) + " 天");
            return base("vaccine_overdue", "疫苗逾期", r.getBizId(), title, content, level,
                    BigDecimal.valueOf(days), r.getDueDate());
        }).toList();
    }

    private WarningVO base(String bizType, String bizTypeName, Long bizId,
                           String title, String content, int level,
                           BigDecimal value, java.time.LocalDate dueDate) {
        return WarningVO.builder()
                .bizType(bizType)
                .bizTypeName(bizTypeName)
                .bizId(bizId == null ? null : String.valueOf(bizId))
                .title(title)
                .content(content)
                .level(level)
                .levelName(levelName(level))
                .value(value)
                .dueDate(dueDate)
                .build();
    }

    private String levelName(int level) {
        return switch (level) {
            case LEVEL_URGENT -> "紧急";
            case LEVEL_WARN -> "警告";
            default -> "提示";
        };
    }

    /**
     * 单个数据源失败不影响其它源 —— 看板宁可少一块，也不能整个打不开。
     */
    private List<WarningVO> safe(Supplier<List<WarningVO>> supplier, String name) {
        try {
            List<WarningVO> rows = supplier.get();
            return rows == null ? List.of() : rows;
        } catch (Exception ex) {
            log.error("[预警中心] 采集「{}」失败，本次跳过该源", name, ex);
            return List.of();
        }
    }

    private int intValue(BigDecimal value) {
        return value == null ? 0 : value.intValue();
    }
}
