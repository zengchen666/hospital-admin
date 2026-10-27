package com.hospital.report.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 报表执行的通用结果。
 *
 * <p>五类内置报表各有各的 VO（门诊看人次、收入看金额、药品看排行），
 * 字段完全不同。如果执行接口按类型返回不同结构，前端就要为每种报表
 * 写一套渲染分支 —— 自定义报表刚想「通用」就被自己的返回结构绑死了。</p>
 *
 * <p>因此执行结果统一为「列定义 + 行数据」：列定义带中文标题和顺序，
 * 行数据用 {@code Map<列键, 值>}。前端一套表格组件通吃所有数据集。</p>
 */
@Getter
@Setter
public class ReportResultVO {

    /** 列定义，顺序即展示顺序 */
    private List<ColumnMeta> columns;

    /** 行数据，每条 Map 的 key 对应 {@link ColumnMeta#getKey()} */
    private List<Map<String, Object>> rows;

    /** 查询区间说明，导出时写进文件头，避免别人拿到一份不知道统计哪段时间的表 */
    private String rangeDesc;

    @Getter
    @Setter
    public static class ColumnMeta {

        /** 列键 */
        private String key;

        /** 列标题（中文） */
        private String title;

        public ColumnMeta() {
        }

        public ColumnMeta(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }
}
