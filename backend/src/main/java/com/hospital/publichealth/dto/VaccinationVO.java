package com.hospital.publichealth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 接种记录视图对象。
 *
 * <p>{@code overdue} 由服务端算：下次接种日期已过就算逾期，
 * 前端不该自己比日期（时区与服务器时间都可能不一致）。</p>
 */
@Getter
@Setter
@Builder
public class VaccinationVO {

    private String id;
    private Long patientId;
    private String patientName;
    private String vaccineName;
    private String batchNo;
    private Integer doseNo;
    private LocalDate vaccinateDate;
    private Long operatorId;
    private String operatorName;
    private LocalDate nextDate;

    /** 下次接种是否已逾期（next_date < 今天） */
    private Boolean overdue;
}
