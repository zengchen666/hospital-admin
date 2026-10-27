package com.hospital.report.service;

import com.alibaba.excel.EasyExcel;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.report.dto.ReportResultVO;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 报表导出：Excel（EasyExcel 流式写）与 PDF（OpenPDF）。
 *
 * <p>导出走 {@link ReportResultVO} 这一份通用结果，而不是为每种报表写一个导出器：
 * 五类报表 × 两种格式 = 十个导出器，任何一处列定义变动都要改十遍。
 * 通用结构下，新增报表只要登记列定义，导出自动可用。</p>
 *
 * <p><b>中文是 PDF 的第一道坑</b>：OpenPDF 内置字体不含中文字形，
 * 不指定 CJK 字体导出的中文会变成空白或方块。这里按候选路径探测系统字体，
 * 全部找不到时降级为内置字体并打 warn —— 宁可导出一份中文乱码的文件，
 * 也不让整个导出接口 500。</p>
 */
@Slf4j
@Service
public class ReportExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 中文字体候选：微软雅黑 → 黑体 → 宋体。ttc 需带索引，故用 ",0" */
    private static final String[] FONT_CANDIDATES = {
            "C:/Windows/Fonts/msyh.ttc,0",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc,0"
    };

    private final String configuredFontPath;

    public ReportExportService(@Value("${hospital.report.pdf-font:}") String configuredFontPath) {
        this.configuredFontPath = configuredFontPath;
    }

    /** 导出格式白名单 */
    public static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "excel";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "excel", "xlsx" -> "excel";
            case "pdf" -> "pdf";
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "导出格式非法：" + format + "，可选值 [excel, pdf]");
        };
    }

    /**
     * 导出为 xlsx。
     *
     * <p>表头下方多写一行区间说明：Excel 会被转发、被另存、被贴进汇报材料，
     * 离开系统之后就没有任何人能再确认它统计的是哪一段时间。</p>
     */
    public byte[] exportExcel(String title, ReportResultVO result) {
        if (result.getColumns() == null || result.getColumns().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "报表没有可导出的列");
        }
        List<List<String>> head = new ArrayList<>();
        for (ReportResultVO.ColumnMeta column : result.getColumns()) {
            head.add(List.of(column.getTitle()));
        }

        List<List<Object>> data = new ArrayList<>();
        List<Object> rangeNote = new ArrayList<>();
        rangeNote.add("统计区间：" + result.getRangeDesc() + "    导出时间：" + STAMP.format(LocalDateTime.now()));
        for (int i = 1; i < result.getColumns().size(); i++) {
            rangeNote.add("");
        }
        data.add(rangeNote);

        for (Map<String, Object> row : result.getRows()) {
            List<Object> line = new ArrayList<>();
            for (ReportResultVO.ColumnMeta column : result.getColumns()) {
                line.add(display(row.get(column.getKey())));
            }
            data.add(line);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).head(head).sheet(sheetName(title)).doWrite(data);
        return out.toByteArray();
    }

    /** 导出为 PDF（A4 横向，列多时不易折行） */
    public byte[] exportPdf(String title, ReportResultVO result) {
        if (result.getColumns() == null || result.getColumns().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "报表没有可导出的列");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            BaseFont baseFont = resolveBaseFont();
            Font titleFont = new Font(baseFont, 16, Font.BOLD);
            Font noteFont = new Font(baseFont, 9, Font.NORMAL);
            Font headFont = new Font(baseFont, 10, Font.BOLD);
            Font cellFont = new Font(baseFont, 9, Font.NORMAL);

            document.add(new Paragraph(title == null ? "统计报表" : title, titleFont));
            document.add(new Paragraph(
                    "统计区间：" + result.getRangeDesc() + "    导出时间：" + STAMP.format(LocalDateTime.now()),
                    noteFont));
            document.add(new Paragraph(" ", noteFont));

            PdfPTable table = new PdfPTable(result.getColumns().size());
            table.setWidthPercentage(100);
            for (ReportResultVO.ColumnMeta column : result.getColumns()) {
                PdfPCell cell = new PdfPCell(new Phrase(column.getTitle(), headFont));
                cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                table.addCell(cell);
            }
            for (Map<String, Object> row : result.getRows()) {
                for (ReportResultVO.ColumnMeta column : result.getColumns()) {
                    PdfPCell cell = new PdfPCell(new Phrase(display(row.get(column.getKey())), cellFont));
                    table.addCell(cell);
                }
            }
            document.add(table);
            document.close();
        } catch (Exception e) {
            // 生成 PDF 失败属于服务端问题，但错误信息里不该带上字体路径等环境细节
            log.error("导出 PDF 失败 title={}", title, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "报表导出失败，请稍后重试");
        }
        return out.toByteArray();
    }

    /**
     * 解析中文字体。
     *
     * <p>配置项优先，其次按候选路径探测。都没有时返回 {@code null}，
     * 调用方用内置字体兜底（中文会显示为空白，但文件仍能打开）。</p>
     */
    private BaseFont resolveBaseFont() {
        List<String> candidates = new ArrayList<>();
        if (configuredFontPath != null && !configuredFontPath.isBlank()) {
            candidates.add(configuredFontPath.trim());
        }
        candidates.addAll(List.of(FONT_CANDIDATES));

        for (String path : candidates) {
            try {
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                log.debug("PDF 字体不可用，尝试下一个 candidate={}", path);
            }
        }
        log.warn("未找到可用的中文字体，导出的 PDF 中文可能显示为空白；"
                + "可通过 hospital.report.pdf-font 指定字体文件路径");
        return null;
    }

    /** 单元格取值：null 统一转成空串，金额不在这里补小数位（Excel 里保留数值便于再计算） */
    private String display(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /** 工作表名去掉 Excel 不允许的字符，并限长 31 */
    private String sheetName(String title) {
        String name = (title == null || title.isBlank()) ? "统计报表" : title.trim();
        String safe = name.replaceAll("[\\\\/\\[\\]?*:]", "_");
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    /** 下载文件名：报告名 + 时间戳，避免同一份报表反复导出时互相覆盖 */
    public String fileName(String title, String format) {
        String base = (title == null || title.isBlank()) ? "统计报表" : title.trim();
        return base + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ("pdf".equals(format) ? ".pdf" : ".xlsx");
    }

    /**
     * RFC 6266 的 {@code Content-Disposition}。
     *
     * <p>中文文件名必须给两版：{@code filename} 是 ASCII 兜底（给老浏览器），
     * {@code filename*} 是 UTF-8 百分号编码（现代浏览器优先取这个）。
     * 只给前者中文会变成下划线，只给后者部分旧客户端直接把整个头扔掉。</p>
     */
    public String contentDisposition(String title, String format) {
        String name = fileName(title, format);
        String ascii = name.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
