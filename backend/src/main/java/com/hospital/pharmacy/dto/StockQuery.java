package com.hospital.pharmacy.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存查询。
 *
 * <p>{@code onlyWarn} 与 {@code expireDays} 是库存页的两个高频视图：
 * 「该补货了」和「该处理近效期了」，做成一个接口的两个开关比拆两个接口好维护。</p>
 */
@Getter
@Setter
public class StockQuery extends PageQuery {

    private Long drugId;

    private Long warehouseId;

    /** 药品名称 / 编码 模糊 */
    private String keyword;

    /** true 时只看 quantity <= warn_num 的批次 */
    private Boolean onlyWarn;

    /** 剩余天数内即将过期的批次，如 90 */
    private Integer expireDays;
}
