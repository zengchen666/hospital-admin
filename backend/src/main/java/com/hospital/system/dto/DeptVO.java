package com.hospital.system.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室下拉选项。
 *
 * <p>只暴露前端选科室需要的三个字段：组织架构里的
 * {@code dept_code} / {@code sort} / 创建人等一律不下发 ——
 * 下拉框不需要的东西给了，迟早会被拿去做别的用途。</p>
 */
@Getter
@Setter
@Builder
public class DeptVO {

    /** 雪花 ID 转字符串：19 位 Long 超过 JS 安全整数 */
    private String id;

    private String parentId;

    private String deptName;
}
