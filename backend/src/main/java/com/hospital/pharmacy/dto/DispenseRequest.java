package com.hospital.pharmacy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊发药请求。
 *
 * <p>只传处方与发药库房：发什么药、发多少由处方决定，
 * 批号由服务端按「近效期优先」自动分配 —— 药师不需要手工挑批号，
 * 也避免手工选择时的错发。</p>
 */
@Getter
@Setter
public class DispenseRequest {

    @NotNull(message = "处方不能为空")
    private Long prescId;

    @NotNull(message = "发药库房不能为空")
    private Long warehouseId;
}
