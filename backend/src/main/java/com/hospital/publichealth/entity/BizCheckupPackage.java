package com.hospital.publichealth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 体检套餐 biz_checkup_package。
 *
 * <p>{@code itemIds} 是「诊疗项目 ID 的逗号分隔文本」，对应 {@code biz_treat_item.id}。
 * 之所以用文本而不是明细表：套餐项极少变动，一期没必要为它再开一张表；
 * 但解析时必须做<b>字符白名单校验</b>（只允许数字与逗号），
 * 否则这串文本会被拼进 SQL 变成注入点。</p>
 */
@Getter
@Setter
@TableName("biz_checkup_package")
public class BizCheckupPackage extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String packageName;

    /** 诊疗项目 ID 列表，逗号分隔 */
    private String itemIds;

    /** 套餐价；体检单只认这个价，不认前端传值 */
    private BigDecimal price;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
