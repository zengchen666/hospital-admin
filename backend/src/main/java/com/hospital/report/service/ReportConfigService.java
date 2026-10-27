package com.hospital.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.report.dto.DrugStatVO;
import com.hospital.report.dto.IncomeStatVO;
import com.hospital.report.dto.InpatientStatVO;
import com.hospital.report.dto.OutpatientStatVO;
import com.hospital.report.dto.PatientStatVO;
import com.hospital.report.dto.ReportConfigQuery;
import com.hospital.report.dto.ReportConfigRequest;
import com.hospital.report.dto.ReportConfigVO;
import com.hospital.report.dto.ReportQuery;
import com.hospital.report.dto.ReportResultVO;
import com.hospital.report.entity.SysReportConfig;
import com.hospital.report.mapper.SysReportConfigMapper;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 自定义报表配置服务：保存「数据集 + 维度 + 展示形式」，并按配置执行查询。
 *
 * <p><b>内置配置与用户配置的区分</b>：内置配置由 {@link ReportService#generateSnapshot}
 * 首次生成快照时自动创建，{@code creator_id} 为空。所有查询内置配置的地方
 * 都带 {@code creator_id IS NULL} 条件 —— 否则用户建了一份同为
 * {@code income} 类型的报表后，快照会挂到用户配置上，
 * 「谁的报表」和「系统快照」就混在一起了。</p>
 */
@Slf4j
@Service
public class ReportConfigService {

    private final SysReportConfigMapper configMapper;
    private final SysUserMapper userMapper;
    private final ReportService reportService;

    public ReportConfigService(SysReportConfigMapper configMapper,
                               SysUserMapper userMapper,
                               ReportService reportService) {
        this.configMapper = configMapper;
        this.userMapper = userMapper;
        this.reportService = reportService;
    }

    /* ---------------- 配置 CRUD ---------------- */

    public PageResult<ReportConfigVO> page(ReportConfigQuery query) {
        IPage<SysReportConfig> page = configMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), userWrapper(query.getReportType()));
        Map<Long, String> names = loadCreatorNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, names));
    }

    public List<ReportConfigVO> list(String reportType) {
        List<SysReportConfig> entities = configMapper.selectList(userWrapper(reportType));
        Map<Long, String> names = loadCreatorNames(entities);
        return entities.stream().map(entity -> toVO(entity, names)).toList();
    }

    public ReportConfigVO detail(Long id) {
        SysReportConfig entity = requireConfig(id);
        return toVO(entity, loadCreatorNames(List.of(entity)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ReportConfigRequest request) {
        // 白名单校验放在落库之前：非法组合要把错误还给调用方，不能存进去再等执行时炸
        ReportType type = ReportType.of(request.getReportType());
        ReportDimension dimension = ReportDimension.match(type, request.getDimension());

        SysReportConfig entity = new SysReportConfig();
        entity.setId(IdWorker.getId());
        entity.setReportName(request.getReportName().trim());
        entity.setReportType(type.getCode());
        entity.setDimension(dimension.getCode());
        entity.setChartType(ReportDimension.normalizeChartType(request.getChartType()));
        entity.setConfigJson(request.getConfigJson());
        entity.setParams(request.getParams());
        entity.setCreatorId(CurrentUser.requireId());
        configMapper.insert(entity);

        log.info("自定义报表已创建 id={} type={} dimension={}", entity.getId(), type.getCode(), dimension.getCode());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReportConfigRequest request) {
        SysReportConfig entity = requireConfig(id);
        if (isBuiltin(entity)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "系统内置报表配置不允许修改");
        }
        ReportType type = ReportType.of(request.getReportType());
        ReportDimension dimension = ReportDimension.match(type, request.getDimension());

        entity.setReportName(request.getReportName().trim());
        entity.setReportType(type.getCode());
        entity.setDimension(dimension.getCode());
        entity.setChartType(ReportDimension.normalizeChartType(request.getChartType()));
        entity.setConfigJson(request.getConfigJson());
        entity.setParams(request.getParams());
        configMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysReportConfig entity = requireConfig(id);
        if (isBuiltin(entity)) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "系统内置报表配置不允许删除");
        }
        configMapper.deleteById(id);
    }

    /* ---------------- 执行 ---------------- */

    /**
     * 按已保存的配置执行查询。
     *
     * @param override 覆盖配置里的默认参数；为空则用配置自带的维度
     */
    public ReportResultVO execute(Long id, ReportQuery query) {
        SysReportConfig entity = requireConfig(id);
        return execute(ReportType.of(entity.getReportType()), entity.getDimension(), query);
    }

    /**
     * 按「数据集 + 维度」执行，不依赖已保存的配置。
     *
     * <p>拖拽画布上的预览走这条：配置还没保存也能先看结果。</p>
     */
    public ReportResultVO execute(String typeCode, String dimensionCode, ReportQuery query) {
        return execute(ReportType.of(typeCode), dimensionCode, query);
    }

    /**
     * 统一执行入口。
     *
     * <p><b>为什么手工拼 Map 而不是 {@code objectMapper.convertValue}</b>：
     * 报表结果要同时喂给接口、Excel 和 PDF 三处。
     * 靠 Jackson 自动转出来的 {@code LocalDate} 有时是字符串、有时是数组，
     * 到了 PDF 就变成一串看不懂的数字。这里每一列都明确取成
     * String / Long / BigDecimal，三种出口的表现完全一致。</p>
     */
    private ReportResultVO execute(ReportType type, String dimensionCode, ReportQuery query) {
        ReportDimension dimension = ReportDimension.match(type, dimensionCode);
        ReportResultVO result = new ReportResultVO();
        result.setRangeDesc(reportService.describeRange(query));

        List<ReportResultVO.ColumnMeta> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        switch (type) {
            case OUTPATIENT -> {
                boolean byDate = dimension == ReportDimension.DATE;
                List<OutpatientStatVO> data = byDate
                        ? reportService.outpatientTrend(query)
                        : reportService.outpatientByDept(query);
                columns.add(new ReportResultVO.ColumnMeta("dimension", byDate ? "日期" : "科室"));
                columns.add(new ReportResultVO.ColumnMeta("regCount", "挂号人次"));
                columns.add(new ReportResultVO.ColumnMeta("patientCount", "就诊人数"));
                columns.add(new ReportResultVO.ColumnMeta("regFeeAmount", "挂号费收入"));
                if (!byDate) {
                    columns.add(new ReportResultVO.ColumnMeta("ratio", "占比(%)"));
                }
                for (OutpatientStatVO row : data) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("dimension", byDate ? str(row.getStatDate()) : row.getDeptName());
                    map.put("regCount", row.getRegCount());
                    map.put("patientCount", row.getPatientCount());
                    map.put("regFeeAmount", row.getRegFeeAmount());
                    if (!byDate) {
                        map.put("ratio", row.getRatio());
                    }
                    rows.add(map);
                }
            }
            case INPATIENT -> {
                InpatientStatVO data = reportService.inpatient(query);
                columns.add(new ReportResultVO.ColumnMeta("inHospitalCount", "在院人数"));
                columns.add(new ReportResultVO.ColumnMeta("admittedCount", "入院人次"));
                columns.add(new ReportResultVO.ColumnMeta("dischargedCount", "出院人次"));
                columns.add(new ReportResultVO.ColumnMeta("availableBeds", "可用床位"));
                columns.add(new ReportResultVO.ColumnMeta("occupiedBeds", "占用床位"));
                columns.add(new ReportResultVO.ColumnMeta("bedUsageRate", "床位使用率(%)"));
                columns.add(new ReportResultVO.ColumnMeta("avgStayDays", "平均住院日"));
                columns.add(new ReportResultVO.ColumnMeta("depositBalance", "在院预交金"));
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("inHospitalCount", data.getInHospitalCount());
                map.put("admittedCount", data.getAdmittedCount());
                map.put("dischargedCount", data.getDischargedCount());
                map.put("availableBeds", data.getAvailableBeds());
                map.put("occupiedBeds", data.getOccupiedBeds());
                map.put("bedUsageRate", data.getBedUsageRate());
                map.put("avgStayDays", data.getAvgStayDays());
                map.put("depositBalance", data.getDepositBalance());
                rows.add(map);
            }
            case INCOME -> {
                boolean byDoctor = dimension == ReportDimension.DOCTOR;
                List<IncomeStatVO> data = reportService.income(byDoctor ? "doctor"
                        : (dimension == ReportDimension.DEPT ? "dept" : "itemType"), query);
                columns.add(new ReportResultVO.ColumnMeta("dimensionName",
                        byDoctor ? "医生" : (dimension == ReportDimension.DEPT ? "科室" : "项目类型")));
                columns.add(new ReportResultVO.ColumnMeta("amount", "收入金额"));
                if (byDoctor) {
                    columns.add(new ReportResultVO.ColumnMeta("visitCount", "接诊人次"));
                    columns.add(new ReportResultVO.ColumnMeta("prescAmount", "开方金额"));
                } else {
                    columns.add(new ReportResultVO.ColumnMeta("chargeCount", "单据数"));
                    columns.add(new ReportResultVO.ColumnMeta("quantity", "项目数量"));
                }
                for (IncomeStatVO row : data) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("dimensionName", row.getDimensionName());
                    map.put("amount", row.getAmount());
                    if (byDoctor) {
                        map.put("visitCount", row.getVisitCount());
                        map.put("prescAmount", row.getPrescAmount());
                    } else {
                        map.put("chargeCount", row.getChargeCount());
                        map.put("quantity", row.getQuantity());
                    }
                    rows.add(map);
                }
            }
            case DRUG -> {
                List<DrugStatVO> data = reportService.drugRanking(query);
                columns.add(new ReportResultVO.ColumnMeta("rank", "排名"));
                columns.add(new ReportResultVO.ColumnMeta("drugName", "药品名称"));
                columns.add(new ReportResultVO.ColumnMeta("spec", "规格"));
                columns.add(new ReportResultVO.ColumnMeta("quantity", "消耗数量"));
                columns.add(new ReportResultVO.ColumnMeta("amount", "销售金额"));
                for (DrugStatVO row : data) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("rank", row.getRank());
                    map.put("drugName", row.getDrugName());
                    map.put("spec", row.getSpec());
                    map.put("quantity", row.getQuantity());
                    map.put("amount", row.getAmount());
                    rows.add(map);
                }
            }
            case PATIENT -> {
                List<PatientStatVO> data = reportService.patient(switch (dimension) {
                    case GENDER -> "gender";
                    case DISEASE -> "disease";
                    case NEW_TREND -> "new";
                    default -> "age";
                }, query);
                columns.add(new ReportResultVO.ColumnMeta("displayName",
                        dimension == ReportDimension.NEW_TREND ? "月份" : "分组"));
                columns.add(new ReportResultVO.ColumnMeta("patientCount", "人数"));
                for (PatientStatVO row : data) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("displayName", row.getDisplayName());
                    map.put("patientCount", row.getPatientCount());
                    rows.add(map);
                }
            }
        }

        result.setColumns(columns);
        result.setRows(rows);
        return result;
    }

    /* ---------------- 内部方法 ---------------- */

    /** 只查「用户自己的报表」；内置配置（creator_id 为空）不出现在列表里 */
    private LambdaQueryWrapper<SysReportConfig> userWrapper(String reportType) {
        return new LambdaQueryWrapper<SysReportConfig>()
                .eq(StringUtils.hasText(reportType), SysReportConfig::getReportType, reportType)
                .isNotNull(SysReportConfig::getCreatorId)
                .orderByDesc(SysReportConfig::getCreateTime);
    }

    private SysReportConfig requireConfig(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "报表配置ID不能为空");
        }
        SysReportConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "报表配置不存在或已删除");
        }
        return entity;
    }

    private boolean isBuiltin(SysReportConfig entity) {
        return entity.getCreatorId() == null;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private ReportConfigVO toVO(SysReportConfig entity, Map<Long, String> creatorNames) {
        ReportConfigVO vo = new ReportConfigVO();
        vo.setId(entity.getId());
        vo.setReportName(entity.getReportName());
        vo.setReportType(entity.getReportType());
        vo.setReportTypeName(typeName(entity.getReportType()));
        vo.setDimension(entity.getDimension());
        vo.setDimensionName(dimensionNameOf(entity.getDimension()));
        vo.setChartType(entity.getChartType());
        vo.setParams(entity.getParams());
        vo.setConfigJson(entity.getConfigJson());
        vo.setCreatorName(entity.getCreatorId() == null ? null : creatorNames.get(entity.getCreatorId()));
        vo.setBuiltin(isBuiltin(entity));
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private String dimensionNameOf(String dimensionCode) {
        if (!StringUtils.hasText(dimensionCode)) {
            return null;
        }
        for (ReportDimension item : ReportDimension.values()) {
            if (item.getCode().equals(dimensionCode)) {
                return item.getName();
            }
        }
        return dimensionCode;
    }

    private String typeName(String reportType) {
        if (!StringUtils.hasText(reportType)) {
            return null;
        }
        for (ReportType item : ReportType.values()) {
            if (item.getCode().equals(reportType)) {
                return item.getName();
            }
        }
        return reportType;
    }

    private Map<Long, String> loadCreatorNames(List<SysReportConfig> entities) {
        List<Long> ids = entities.stream()
                .map(SysReportConfig::getCreatorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        List<SysUser> users = userMapper.selectByIds(ids);
        if (users != null) {
            for (SysUser user : users) {
                names.put(user.getId(), user.getRealName());
            }
        }
        return names;
    }
}
