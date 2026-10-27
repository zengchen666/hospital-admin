package com.hospital.publichealth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 健康档案新建 / 更新入参。
 *
 * <p>慢病这里只做展示用文本，真正的随访记录走 {@code biz_follow_up}。</p>
 */
@Getter
@Setter
public class HealthRecordRequest {

    @NotNull(message = "患者不能为空")
    private Long patientId;

    /** 慢病，逗号分隔 */
    private String chronicDisease;

    /** 吸烟：0 否 / 1 是 */
    private Integer smoking;

    /** 饮酒：0 否 / 1 是 */
    private Integer drinking;

    /** 运动频率 */
    private String exercise;
}
