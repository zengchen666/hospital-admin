package com.hospital.inpatient.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 护理记录新增请求。
 *
 * <p>跨字段校验（type=1 要 content、type=2/3 要体征）放 Service 里做，
 * 不放这里 —— Bean Validation 的跨字段约束写起来别扭，而规则本身只有两行。</p>
 */
@Getter
@Setter
public class NursingRequest {

    @NotNull(message = "住院记录不能为空")
    private Long admissionId;

    /** 1 护理记录 / 2 体温单 / 3 生命体征 */
    @NotNull(message = "记录类型不能为空")
    @Min(value = 1, message = "记录类型取值非法")
    @Max(value = 3, message = "记录类型取值非法")
    private Integer recordType;

    /** 记录时间，不传取当前时间 */
    private LocalDateTime recordTime;

    /** 体温（℃），合理区间 25~45 */
    @Min(value = 25, message = "体温取值超出合理范围")
    @Max(value = 45, message = "体温取值超出合理范围")
    private BigDecimal temperature;

    @Min(value = 0, message = "脉搏不能为负")
    @Max(value = 300, message = "脉搏取值超出合理范围")
    private Integer pulse;

    @Min(value = 0, message = "呼吸不能为负")
    @Max(value = 100, message = "呼吸取值超出合理范围")
    private Integer breath;

    /** 血压，如 120/80 */
    @Pattern(regexp = "^\\d{2,3}/\\d{2,3}$", message = "血压格式应为 收缩压/舒张压，如 120/80")
    private String bloodPressure;

    /** 血氧饱和度（%） */
    @Min(value = 0, message = "血氧饱和度不能为负")
    @Max(value = 100, message = "血氧饱和度取值超出合理范围")
    private Integer spo2;

    @Min(value = 0, message = "入量不能为负")
    private Integer intake;

    @Min(value = 0, message = "出量不能为负")
    private Integer output;

    @Size(max = 2000, message = "护理记录内容长度不能超过 2000")
    private String content;
}
