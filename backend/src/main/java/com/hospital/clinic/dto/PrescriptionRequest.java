package com.hospital.clinic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 开处方请求。
 *
 * <p><b>只传药品 ID 与用法用量</b>：药品名称、规格、单价、小计全部由服务端
 * 从药品表取快照并计算。前端传价格的话，改一个请求体就能一分钱买药。</p>
 */
@Getter
@Setter
public class PrescriptionRequest {

    @NotNull(message = "就诊 ID 不能为空")
    private Long visitId;

    /** 1 西药 / 2 中药 / 3 中成药 */
    @NotNull(message = "处方类型不能为空")
    @Min(value = 1, message = "处方类型取值非法")
    @Max(value = 3, message = "处方类型取值非法")
    private Integer prescType;

    @Size(max = 500, message = "诊断长度不能超过 500")
    private String diagnosis;

    @NotEmpty(message = "处方至少需要一条药品明细")
    @Valid
    private List<PrescriptionItemRequest> items;
}
