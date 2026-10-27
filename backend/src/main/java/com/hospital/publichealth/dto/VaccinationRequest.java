package com.hospital.publichealth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 疫苗接种登记入参。
 *
 * <p>{@code doseNo} 允许为空：不传时由服务端按「已接种最大剂次 + 1」推算，
 * 避免护士记错剂次。传了就必须校验不重复。</p>
 */
@Getter
@Setter
public class VaccinationRequest {

    @NotNull(message = "患者不能为空")
    private Long patientId;

    @NotBlank(message = "疫苗名称不能为空")
    private String vaccineName;

    /** 疫苗批号，必须存在于库存 */
    private String batchNo;

    /** 剂次，为空则按历史推算 */
    private Integer doseNo;

    @NotNull(message = "接种日期不能为空")
    private LocalDate vaccinateDate;

    /** 下次接种日期 */
    private LocalDate nextDate;
}
