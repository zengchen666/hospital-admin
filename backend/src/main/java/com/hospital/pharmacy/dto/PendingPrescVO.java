package com.hospital.pharmacy.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待发药处方（药房窗口的工作队列）。
 *
 * <p>带上明细是因为窗口发药时本来就要看「发哪几个药、各多少」，
 * 让前端拿处方 ID 再查一次明细，等于每次发药多一次往返。</p>
 */
@Getter
@Builder
public class PendingPrescVO {

    private final String prescId;

    private final String prescNo;

    private final String patientId;

    private final String patientName;

    private final String doctorId;

    private final String doctorName;

    private final String diagnosis;

    private final BigDecimal totalAmount;

    /** 1 西药 / 2 中药 / 3 中成药 */
    private final Integer prescType;

    /** 固定为 2（已收费待发药） */
    private final Integer status;

    private final LocalDateTime createTime;

    private final List<PendingItemVO> items;

    @Getter
    @Builder
    public static class PendingItemVO {

        private final String drugId;

        private final String drugName;

        private final String spec;

        private final String unit;

        private final Integer quantity;

        private final BigDecimal price;

        private final BigDecimal amount;

        /** 当前可用库存合计（跨库房），供窗口判断能不能发 */
        private final Integer availableStock;
    }
}
