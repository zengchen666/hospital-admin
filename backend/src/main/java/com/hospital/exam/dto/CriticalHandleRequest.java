package com.hospital.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 危急值处理登记请求。
 *
 * <p>处理措施 {@code remark} 用 {@code @NotBlank} 而不是 {@code @NotNull}：
 * 空串也能通过 {@code @NotNull}，那样会留下一条「处理了但不知道做了什么」的记录，
 * 危急值闭环的意义恰恰在于留痕。</p>
 */
@Getter
@Setter
public class CriticalHandleRequest {

    @NotNull(message = "报告 ID 不能为空")
    private Long reportId;

    @NotBlank(message = "处理措施不能为空")
    @Size(max = 255, message = "处理措施长度不能超过 255")
    private String remark;
}
