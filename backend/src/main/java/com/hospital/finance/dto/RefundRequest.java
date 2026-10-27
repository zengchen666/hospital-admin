package com.hospital.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 退费申请请求。
 *
 * <p><b>必须逐项指定退哪些、退几件</b>，不接受只传一个总金额。
 * 「这张单退 80 块」这种请求没法回冲明细，也没法退库存，
 * 最后只能靠人工翻原始单据对账。</p>
 *
 * <p>金额由服务端按原收费明细单价算出（数量 × 原单价），前端传的
 * {@code amount} 只作为校验参照，不一致就拒绝。</p>
 */
@Getter
@Setter
public class RefundRequest {

    @NotNull(message = "原收费单不能为空")
    private Long chargeId;

    @NotBlank(message = "退费原因必填")
    @Size(max = 255, message = "退费原因长度不能超过 255")
    private String reason;

    @NotEmpty(message = "退费明细不能为空")
    @Valid
    private List<RefundItemRequest> items;

    @Getter
    @Setter
    public static class RefundItemRequest {

        @NotNull(message = "收费明细不能为空")
        private Long chargeItemId;

        @NotNull(message = "退数量不能为空")
        @Min(value = 1, message = "退数量至少为 1")
        private Integer quantity;
    }
}
