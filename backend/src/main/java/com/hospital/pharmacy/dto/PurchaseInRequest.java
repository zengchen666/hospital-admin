package com.hospital.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购入库请求。
 *
 * <p>每个明细都要带批号与有效期 —— 入库是库存批次的诞生时刻，
 * 这个时候不登记批号，后面发药按批号扣减就无从谈起。</p>
 *
 * <p>{@code arriveNum} 允许小于采购数量（少发 / 破损拒收），
 * 但<b>不允许为 0 或负</b>：一条实到 0 的明细意味着「这笔采购什么都没到」，
 * 那应该在明细里删掉，而不是留一条 0 行混淆对账。</p>
 */
@Getter
@Setter
public class PurchaseInRequest {

    /** 入库批次的统一生产日期，不传则留空（部分药品只标有效期） */
    private LocalDate produceDate;

    /** 预警数量，不传取 0 */
    private Integer warnNum;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    @Valid
    @NotEmpty(message = "入库明细不能为空")
    private List<PurchaseInItemRequest> items;

    @Getter
    @Setter
    public static class PurchaseInItemRequest {

        @NotNull(message = "明细不能为空")
        private Long itemId;

        /** 实到数量，必须大于 0 且不超过采购数量 */
        @NotNull(message = "实到数量不能为空")
        private Integer arriveNum;

        /**
         * 批号必填且不能是空串。
         *
         * <p>用 {@code @NotBlank} 而不是 {@code @NotNull}：空串能通过 NotNull，
         * 结果就是一批「批号为空白」的库存 —— 看起来入库成功了，
         * 但药品召回时这批货定位不到，只能整库报废。</p>
         */
        @NotBlank(message = "批号不能为空")
        @Size(max = 50, message = "批号长度不能超过 50")
        private String batchNo;

        @NotNull(message = "有效期不能为空")
        private LocalDate expireDate;
    }
}
