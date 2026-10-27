package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 入库请求。
 *
 * <p><b>批号必填</b>：药品追溯靠批号。批号为空的库存一旦出问题（召回、不良反应），
 * 只能整库报废。这里直接拒绝，而不是允许「先入进去以后再补」——
 * 「以后」通常不会来。</p>
 *
 * <p><b>有效期必须晚于生产日期</b>，且不接受已过期的批次入库。</p>
 */
@Getter
@Setter
public class StockInRequest {

    @NotNull(message = "药品不能为空")
    private Long drugId;

    @NotNull(message = "库房不能为空")
    private Long warehouseId;

    @NotBlank(message = "批号不能为空")
    @Size(max = 50, message = "批号长度不能超过 50")
    private String batchNo;

    @NotNull(message = "入库数量不能为空")
    @Min(value = 1, message = "入库数量必须大于 0")
    private Integer quantity;

    private LocalDate produceDate;

    @NotNull(message = "有效期不能为空")
    private LocalDate expireDate;

    /** 预警数量，不传取 0（表示不预警） */
    @Min(value = 0, message = "预警数量不能为负")
    private Integer warnNum;

    /** 来源单号，如采购单号 */
    @Size(max = 50, message = "来源单号长度不能超过 50")
    private String relatedNo;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
