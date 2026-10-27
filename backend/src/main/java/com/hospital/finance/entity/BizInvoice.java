package com.hospital.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票 biz_invoice。
 *
 * <p><b>一张收费单最多一张有效发票</b>：金额按收费单实收金额快照，
 * 不跟着药品调价变 —— 发票是给患者的凭证，开出去那一刻就定了。</p>
 *
 * <p><b>作废是改状态，不是删记录</b>：发票号已经报税，
 * 物理删掉会让「这张票去哪了」永远查不到。</p>
 */
@Getter
@Setter
@TableName("biz_invoice")
public class BizInvoice extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String invoiceNo;

    private Long chargeId;

    /** 1 电子 / 2 纸质 */
    private Integer invoiceType;

    private BigDecimal amount;

    /** 0 正常 / 1 已作废 */
    private Integer status;

    private LocalDateTime printTime;
}
