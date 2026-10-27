package com.hospital.exam.dto;

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
 * 开检查检验单请求。
 *
 * <p>申请医生取当前登录用户，不接受前端传值 —— 否则可以替别人开单。</p>
 */
@Getter
@Setter
public class ExamApplyRequest {

    @NotNull(message = "就诊 ID 不能为空")
    private Long visitId;

    /** 1 检验 / 2 检查（影像） */
    @NotNull(message = "检查类型不能为空")
    @Min(value = 1, message = "检查类型取值非法")
    @Max(value = 2, message = "检查类型取值非法")
    private Integer examType;

    @Size(max = 100, message = "项目名称长度不能超过 100")
    private String examItem;

    @Size(max = 50, message = "检查部位长度不能超过 50")
    private String bodyPart;

    @Size(max = 500, message = "临床诊断长度不能超过 500")
    private String clinicalDiagnosis;

    @NotEmpty(message = "至少需要一个检查项目")
    @Valid
    private List<ExamItemRequest> items;
}
