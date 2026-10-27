package com.hospital.publichealth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 出具体检报告入参。
 *
 * <p>{@code summary} 必填：体检报告没有结论等于没出，
 * 受检者拿到一份空白报告会直接回来投诉。</p>
 */
@Getter
@Setter
public class CheckupReportRequest {

    @NotBlank(message = "健康评估结论不能为空")
    private String summary;

    /** 异常项，逗号分隔 */
    private String abnormalItems;

    /** 报告 PDF 文件 ID，一期未接文件服务，预留 */
    private Long reportFileId;
}
