package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 出库请求（报损 / 科室领用 / 调拨等非发药场景）。
 *
 * <p><b>发药不走这里</b>：发药由 DispenseService 统一处理，
 * 因为它还要写发药单、推进处方状态。这里的出库只管库存与流水，
 * 混进来会导致发药单缺失、处方状态卡住。</p>
 */
@Getter
@Setter
public class StockOutRequest {

    @NotNull(message = "药品不能为空")
    private Long drugId;

    @NotNull(message = "库房不能为空")
    private Long warehouseId;

    @NotBlank(message = "批号不能为空")
    @Size(max = 50, message = "批号长度不能超过 50")
    private String batchNo;

    @NotNull(message = "出库数量不能为空")
    @Min(value = 1, message = "出库数量必须大于 0")
    private Integer quantity;

    /** 来源单号，如报损单号 */
    @Size(max = 50, message = "来源单号长度不能超过 50")
    private String relatedNo;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
