package com.hospital.exam.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 样本条码（打印用）。
 *
 * <p>条码号在采样时生成、重复打印保持同一号 ——
 * 同一管血印出两个不同条码，等于让标本在传递链上有了两个身份，
 * 出了化验事故连追溯都做不了。</p>
 */
@Getter
@Builder
public class ExamBarcodeVO {

    private final String applyId;

    private final String applyNo;

    private final String barcodeNo;

    private final String patientId;

    private final String patientName;

    /** 1 检验 / 2 检查（影像） */
    private final Integer examType;

    private final String examItem;

    private final String bodyPart;

    /** 该申请下的所有项目名称，打印时贴在条码下方便于核对 */
    private final List<String> itemNames;
}
