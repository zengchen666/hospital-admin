package com.hospital.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 样本采集请求。
 *
 * <p>目前只有申请单 ID，包一层对象是为了给后续留扩展位
 * （采血量、采集部位、采集人签名），届时不用动接口签名。</p>
 */
@Getter
@Setter
public class SampleRequest {

    @NotNull(message = "申请单不能为空")
    private Long applyId;
}
