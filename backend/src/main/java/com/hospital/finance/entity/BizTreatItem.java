package com.hospital.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 诊疗项目（价目表）biz_treat_item。
 *
 * <p><b>价格带生效日期，同一个 {@code item_code} 会有多个价格版本</b>。
 * 取价必须按「生效日期不晚于今天的最新一版」，直接按 code 查会随机命中
 * 某一版 —— 收的是调价前的旧钱还是调价后的新钱，全看数据库返回顺序。</p>
 */
@Getter
@Setter
@TableName("biz_treat_item")
public class BizTreatItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String itemCode;

    private String itemName;

    /** 1 诊疗 / 2 检查 / 3 检验 / 4 床位 / 5 手术 / 6 材料 */
    private Integer itemType;

    private String spec;

    private String unit;

    private BigDecimal price;

    /** 1 甲类 / 2 乙类 / 3 丙类（自费） */
    private Integer insuranceType;

    private BigDecimal insuranceRatio;

    private Long deptId;

    /** 0 停用 / 1 启用 */
    private Integer status;

    private LocalDate effectiveDate;
}
