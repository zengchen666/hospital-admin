package com.hospital.patient.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 患者敏感信息明文视图（仅「解密查看」接口返回，需 patient:decrypt 权限且记审计）。
 */
@Getter
@Builder
public class PatientPlainVO {

    private final String id;

    private final String patientNo;

    private final String name;

    private final String idCard;

    private final String phone;
}
