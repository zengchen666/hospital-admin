package com.hospital.report.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.report.dto.ReportResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 报表导出：Excel / PDF 两种格式的产出校验。
 *
 * <p>这里不解析文件内容（那是 POI/OpenPDF 自己的测试职责），
 * 只守住三件事：
 * <ol>
 *   <li>两种格式都能产出<b>合法的文件魔数</b>（xlsx 是 zip 的 PK，pdf 是 %PDF）
 *       —— 魔数错了说明写的是空流或被异常吞掉后返回了空数组；</li>
 *   <li>没有列时直接报错，不产出一个「能打开但什么都没有」的文件；</li>
 *   <li>中文文件名在响应头里既给 ASCII 兜底也给 UTF-8 版本。</li>
 * </ol>
 */
class ReportExportServiceTest {

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService("");
    }

    private ReportResultVO result() {
        ReportResultVO result = new ReportResultVO();
        result.setRangeDesc("2026-01-01 ~ 2026-01-31");
        result.setColumns(List.of(
                new ReportResultVO.ColumnMeta("deptName", "科室"),
                new ReportResultVO.ColumnMeta("amount", "收入金额")));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deptName", "内科");
        row.put("amount", new BigDecimal("1234.50"));
        result.setRows(List.of(row));
        return result;
    }

    @Test
    @DisplayName("导出 Excel 产出合法的 xlsx（zip 魔数 PK）")
    void exportExcelProducesXlsx() {
        byte[] bytes = service.exportExcel("收入统计", result());

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
    }

    @Test
    @DisplayName("导出 PDF 产出合法的 PDF（魔数 %PDF）")
    void exportPdfProducesPdf() {
        byte[] bytes = service.exportPdf("收入统计", result());

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("没有列时拒绝导出，避免产出空壳文件")
    void emptyColumnsAreRejected() {
        ReportResultVO empty = new ReportResultVO();
        empty.setColumns(List.of());
        empty.setRows(List.of());

        assertThatThrownBy(() -> service.exportExcel("空报表", empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表没有可导出的列");

        assertThatThrownBy(() -> service.exportPdf("空报表", empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表没有可导出的列");
    }

    @Test
    @DisplayName("导出格式走白名单，xlsx 归一化为 excel")
    void formatIsWhitelisted() {
        assertThat(ReportExportService.normalizeFormat(null)).isEqualTo("excel");
        assertThat(ReportExportService.normalizeFormat(" XLSX ")).isEqualTo("excel");
        assertThat(ReportExportService.normalizeFormat("PDF")).isEqualTo("pdf");

        assertThatThrownBy(() -> ReportExportService.normalizeFormat("csv"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出格式非法");
    }

    @Test
    @DisplayName("中文文件名同时给 ASCII 兜底与 UTF-8 编码两版")
    void contentDispositionHasBothEncodings() {
        String header = service.contentDisposition("收入统计", "excel");

        assertThat(header).startsWith("attachment;");
        assertThat(header).contains("filename=");
        assertThat(header).contains("filename*=UTF-8''");
        // 响应头必须是纯 ASCII：中文只能以百分号编码的形式出现在 filename* 里
        assertThat(header).doesNotContain("收入统计");
    }

    @Test
    @DisplayName("工作表名清理掉 Excel 不允许的字符")
    void sheetNameIsSanitized() {
        // 通过导出来间接验证：非法字符会直接让 EasyExcel 抛异常
        byte[] bytes = service.exportExcel("收入/统计[1]", result());
        assertThat(bytes).isNotEmpty();
    }
}
