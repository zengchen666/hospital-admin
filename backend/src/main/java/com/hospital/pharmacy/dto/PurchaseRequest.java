package com.hospital.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建采购单（含明细）。
 *
 * <p>状态由服务端决定：默认落「待审核」而不是「草稿」——
 * 采购员建单的目的就是要买，再点一次「提交审核」纯属多余点击。</p>
 */
@Getter
@Setter
public class PurchaseRequest {

    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    @NotNull(message = "目标库房不能为空")
    private Long warehouseId;

    private LocalDate expectDate;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    @Valid
    @NotEmpty(message = "采购明细不能为空")
    private List<PurchaseItemRequest> items;
}
