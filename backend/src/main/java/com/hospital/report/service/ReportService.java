package com.hospital.report.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.report.dto.DrugStatVO;
import com.hospital.report.dto.IncomeStatVO;
import com.hospital.report.dto.InpatientStatVO;
import com.hospital.report.dto.OutpatientStatVO;
import com.hospital.report.dto.PatientStatVO;
import com.hospital.report.dto.ReportQuery;
import com.hospital.report.entity.SysReportConfig;
import com.hospital.report.entity.SysReportData;
import com.hospital.report.mapper.ReportStatMapper;
import com.hospital.report.mapper.SysReportConfigMapper;
import com.hospital.report.mapper.SysReportDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * 统计报表服务。
 *
 * <p>本类的每一行都在防同一件事：<b>报表把生产库拖垮</b>。
 * 日期范围强制收敛（默认 30 天、上限 366 天）、报表类型走白名单、
 * 聚合全部下推 SQL。</p>
 *
 * <p>另外，报表数字必须<b>可解释</b>：退号不算门诊量、未支付不算收入、
 * 作废处方不算绩效、维修床不进使用率分母。这些口径都写在 SQL 注释里，
 * 不写清楚的话，下一个人「顺手优化」就会把它们改掉。</p>
 */
@Slf4j
@Service
public class ReportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    private final ReportStatMapper statMapper;
    private final SysReportConfigMapper configMapper;
    private final SysReportDataMapper dataMapper;
    private final ObjectMapper objectMapper;

    public ReportService(ReportStatMapper statMapper,
                         SysReportConfigMapper configMapper,
                         SysReportDataMapper dataMapper,
                         ObjectMapper objectMapper) {
        this.statMapper = statMapper;
        this.configMapper = configMapper;
        this.dataMapper = dataMapper;
        this.objectMapper = objectMapper;
    }

    /** 查询区间（左闭右开） */
    private record Range(LocalDateTime start, LocalDateTime end) {
    }

    /* ---------------- 门诊统计 ---------------- */

    public List<OutpatientStatVO> outpatientByDept(ReportQuery query) {
        Range range = resolveRange(query);
        List<OutpatientStatVO> rows = statMapper.outpatientByDept(range.start(), range.end(), query.getDeptId());
        fillRatio(rows);
        return rows;
    }

    public List<OutpatientStatVO> outpatientTrend(ReportQuery query) {
        Range range = resolveRange(query);
        return statMapper.outpatientTrend(range.start(), range.end(), query.getDeptId());
    }

    /* ---------------- 住院统计 ---------------- */

    public InpatientStatVO inpatient(ReportQuery query) {
        Range range = resolveRange(query);
        InpatientStatVO vo = statMapper.inpatientSummary(range.start(), range.end());
        vo.setBedUsageRate(rate(vo.getOccupiedBeds(), vo.getAvailableBeds()));
        return vo;
    }

    /* ---------------- 收入统计 ---------------- */

    public List<IncomeStatVO> income(String dimension, ReportQuery query) {
        Range range = resolveRange(query);
        return switch (normalize(dimension, "itemType", "dept", "doctor")) {
            case "dept" -> statMapper.incomeByDept(range.start(), range.end());
            case "doctor" -> statMapper.doctorPerformance(range.start(), range.end(), query.getTopN());
            default -> statMapper.incomeByItemType(range.start(), range.end());
        };
    }

    /* ---------------- 药品统计 ---------------- */

    public List<DrugStatVO> drugRanking(ReportQuery query) {
        Range range = resolveRange(query);
        List<DrugStatVO> rows = statMapper.drugRanking(range.start(), range.end(), query.getTopN());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        return rows;
    }

    /* ---------------- 患者分析 ---------------- */

    public List<PatientStatVO> patient(String dimension, ReportQuery query) {
        Range range = resolveRange(query);
        return switch (normalize(dimension, "age", "gender", "disease", "new")) {
            case "gender" -> statMapper.patientGenderDistribution();
            case "disease" -> statMapper.diseaseRanking(range.start(), range.end(), query.getTopN());
            case "new" -> statMapper.newPatientTrend(range.start(), range.end());
            default -> statMapper.patientAgeDistribution();
        };
    }

    /* ---------------- 预聚合快照 ---------------- */

    /**
     * 生成某一天的预聚合快照。
     *
     * <p><b>为什么需要快照</b>：明细会变。今天看「上月经营日报」是 120 万，
     * 下个月有人退了一笔费，再查变成 119 万 —— 同一份报表两个数字，
     * 谁也说不清哪个对。快照把历史口径钉死在生成那一刻。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generateSnapshot(String typeCode, LocalDate date) {
        ReportType type = ReportType.of(typeCode);
        LocalDate target = date == null ? LocalDate.now().minusDays(1) : date;
        if (target.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能为未来日期生成快照");
        }

        String json = switch (type) {
            case OUTPATIENT -> toJson(outpatientByDept(singleDay(target)));
            case INPATIENT -> toJson(List.of(inpatient(singleDay(target))));
            case INCOME -> toJson(income("itemType", singleDay(target)));
            case DRUG -> toJson(drugRanking(singleDay(target)));
            case PATIENT -> toJson(patient("age", singleDay(target)));
        };

        SysReportData data = new SysReportData();
        // 自定义 @Insert 不会触发 MP 的雪花 ID 填充，必须手动给
        data.setId(IdWorker.getId());
        data.setReportId(resolveConfigId(type));
        data.setStatDate(target);
        data.setDataJson(json);
        dataMapper.upsert(data);

        log.info("报表快照已生成 type={} date={} bytes={}", type.getCode(), target, json.length());
        return data.getId();
    }

    /** 读取快照；未生成时返回 null，由 Controller 转成 data=null */
    public String getSnapshot(String typeCode, LocalDate date) {
        ReportType type = ReportType.of(typeCode);
        LocalDate target = date == null ? LocalDate.now().minusDays(1) : date;
        Long reportId = resolveConfigId(type);
        SysReportData data = dataMapper.selectByDate(reportId, target);
        return data == null ? null : data.getDataJson();
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 解析并校验查询区间。
     *
     * <p>这是整个报表模块最重要的一段防御：没有它，一个不传日期的查询
     * 就会变成全表 {@code GROUP BY}。宁可报错让使用者填日期，
     * 也不能让报表接口有能力独自拖垮主库。</p>
     */
    private Range resolveRange(ReportQuery query) {
        LocalDate end = query.getEndTime() == null ? LocalDate.now() : query.getEndTime();
        LocalDate start = query.getStartTime() == null
                ? end.minusDays(ReportQuery.DEFAULT_RANGE_DAYS - 1L)
                : query.getStartTime();

        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > ReportQuery.MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "查询区间不能超过 " + ReportQuery.MAX_RANGE_DAYS + " 天，当前 " + days + " 天");
        }
        // 左闭右开：end 当天 23:59:59.999 也要算进来，用次日零点做边界
        return new Range(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    /**
     * 查询区间的文字说明（含首尾日期）。
     *
     * <p>导出文件与快照都要带上它：一份没有时间范围的报表等于没有单位，
     * 被转发三次之后没人知道里面是「今天」还是「去年」。</p>
     */
    public String describeRange(ReportQuery query) {
        Range range = resolveRange(query);
        // end 是次日零点，减一天回到使用者填的那个结束日期
        return range.start().toLocalDate() + " ~ " + range.end().minusDays(1).toLocalDate();
    }

    private ReportQuery singleDay(LocalDate date) {
        ReportQuery query = new ReportQuery();
        query.setStartTime(date);
        query.setEndTime(date);
        return query;
    }

    /** 维度参数白名单，非法值直接 40001 —— 不让前端的字符串流进 switch 默认分支 */
    private String normalize(String dimension, String... allowed) {
        if (dimension == null || dimension.isBlank()) {
            return allowed[0];
        }
        String trimmed = dimension.trim().toLowerCase(Locale.ROOT);
        for (String item : allowed) {
            if (item.equalsIgnoreCase(trimmed)) {
                return item;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID,
                "统计维度非法：" + dimension + "，可选值 " + List.of(allowed));
    }

    /** 占全院比例，除不尽保留两位小数；分母为 0 时返回 0 而不是抛异常 */
    private void fillRatio(List<OutpatientStatVO> rows) {
        long total = rows.stream().mapToLong(row -> row.getRegCount() == null ? 0L : row.getRegCount()).sum();
        for (OutpatientStatVO row : rows) {
            row.setRatio(rate(row.getRegCount(), total));
        }
    }

    private BigDecimal rate(Number numerator, Number denominator) {
        long top = numerator == null ? 0L : numerator.longValue();
        long bottom = denominator == null ? 0L : denominator.longValue();
        if (bottom <= 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return BigDecimal.valueOf(top)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(bottom), SCALE, RoundingMode.HALF_UP);
    }

    /** 取报表配置 ID；首次生成时自动建一条默认配置 */
    private Long resolveConfigId(ReportType type) {
        SysReportConfig config = configMapper.selectByType(type.getCode());
        if (config != null) {
            return config.getId();
        }
        SysReportConfig created = new SysReportConfig();
        created.setId(IdWorker.getId());
        created.setReportName(type.getName());
        created.setReportType(type.getCode());
        configMapper.insert(created);
        return created.getId();
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            // 报表数据里出现无法序列化的类型属于编码错误，不该让调用方拿到半个 JSON
            log.error("报表快照序列化失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "报表数据序列化失败");
        }
    }
}
