package com.hospital.clinic.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 处方明细视图对象。
 */
@Getter
@Builder
public class PrescriptionItemVO {

    private final String id;

    private final Long drugId;

    private final String drugName;

    private final String spec;

    private final String usage;

    private final String dosage;

    private final String frequency;

    private final Integer days;

    private final Integer quantity;

    private final BigDecimal price;

    private final BigDecimal amount;
}
