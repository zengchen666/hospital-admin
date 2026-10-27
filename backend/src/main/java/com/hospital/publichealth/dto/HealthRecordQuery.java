package com.hospital.publichealth.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 健康档案分页查询。
 *
 * <p>{@code keyword} 同时匹配档案号与患者姓名；{@code chronicDisease} 走
 * {@code LIKE '%xx%'} —— 慢病是逗号分隔文本，只能模糊匹配，
 * 数据量有限（与居民数同量级），可接受。真实的高频筛选应走字典表。</p>
 */
@Getter
@Setter
public class HealthRecordQuery extends PageQuery {

    private Long patientId;

    /** 慢病关键字 */
    private String chronicDisease;

    /** 是否吸烟：0 否 / 1 是 */
    private Integer smoking;
}
