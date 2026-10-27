package com.hospital.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 报告录入请求。
 *
 * <p>{@code isCritical} 必须由录入者显式判断，不能靠系统自动阈值猜 ——
 * 检验危急值阈值因项目、机构而异，漏报的代价是患者生命。</p>
 */
@Getter
@Setter
public class ExamResultRequest {

    @NotNull(message = "申请单不能为空")
    private Long applyId;

    @Size(max = 2000, message = "结果摘要长度不能超过 2000")
    private String result;

    @Size(max = 500, message = "结论长度不能超过 500")
    private String conclusion;

    /** 0 否 / 1 是 */
    private Integer isCritical;

    @NotEmpty(message = "至少需要一条检验结果")
    @Valid
    private List<ExamResultItemRequest> items;
}
