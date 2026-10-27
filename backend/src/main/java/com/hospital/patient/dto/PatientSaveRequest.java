package com.hospital.patient.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者建档 / 修改请求。
 *
 * <p>身份证与手机号在这里是<b>明文入参</b>，服务端负责计算 hash 与（将来的）加密存储，
 * 响应一律脱敏，避免明文在列表接口里反复流通。</p>
 */
@Getter
@Setter
public class PatientSaveRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String name;

    @NotNull(message = "性别不能为空")
    @Min(value = 0, message = "性别取值非法")
    @Max(value = 2, message = "性别取值非法")
    private Integer gender;

    private LocalDate birthDate;

    /** 身份证号：18 位，末位可为 X */
    @Pattern(regexp = "^$|^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 255, message = "住址长度不能超过 255")
    private String address;

    @Size(max = 10, message = "血型取值过长")
    private String bloodType;

    @Size(max = 500, message = "过敏史长度不能超过 500")
    private String allergyHistory;

    private String medicalHistory;

    private String familyHistory;

    @Size(max = 255, message = "标签长度不能超过 255")
    private String tags;
}
