package com.hospital.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商 biz_supplier。
 *
 * <p><b>停用不等于删除</b>：供应商带着历史采购单，删了以后采购单上的
 * {@code supplier_id} 就变成了指不到人的空指针，对账时查不出货是谁供的。
 * 不想合作了置 0 停用即可。</p>
 */
@Getter
@Setter
@TableName("biz_supplier")
public class BizSupplier extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String supplierName;

    /** 供应商编码，唯一 */
    private String supplierCode;

    private String contactName;

    private String contactPhone;

    private String address;

    /** 经营许可证号 */
    private String licenseNo;

    /** 信用等级 1-5，5 最优 */
    private Integer creditLevel;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
