package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 病历保存请求。
 *
 * <p>{@code submit = true} 表示提交（状态 0 → 1），此时主诉与诊断必填；
 * {@code false} 则存草稿，允许中途离开再回来继续写。</p>
 */
@Getter
@Setter
public class MedicalRecordRequest {

    @NotNull(message = "就诊 ID 不能为空")
    private Long visitId;

    @Size(max = 500, message = "主诉长度不能超过 500")
    private String chiefComplaint;

    private String presentIllness;

    private String physicalExam;

    @Size(max = 500, message = "诊断长度不能超过 500")
    private String diagnosis;

    @Size(max = 20, message = "ICD 编码长度不能超过 20")
    private String icdCode;

    /** true 提交（不可再改）/ false 存草稿 */
    private Boolean submit;
}
