package com.hospital.finance.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目查询条件。
 *
 * <p>{@code onlyEffective} 用于「只看当前生效价」，
 * 否则同一个编码的历史版本会占满列表，看不清现在收多少钱。</p>
 */
@Getter
@Setter
public class TreatItemQuery extends PageQuery {

    /** 同时匹配编码与名称 */
    private String keyword;

    /** 1 诊疗 / 2 检查 / 3 检验 / 4 床位 / 5 手术 / 6 材料 */
    private Integer itemType;

    /** 0 停用 / 1 启用 */
    private Integer status;

    /** true 时按 item_code 去重，只保留生效日期最新且不晚于今天的一版 */
    private Boolean onlyEffective;

    private Long deptId;
}
