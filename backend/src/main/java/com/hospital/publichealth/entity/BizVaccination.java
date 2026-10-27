package com.hospital.publichealth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 疫苗接种记录 biz_vaccination。
 *
 * <p><b>批号不是可选项</b>：疫苗出现质量问题追溯时，靠的就是批号定位到
 * 「哪些人打了哪一批」。批号必须从库存里真实存在的批号中取，手写会追溯不到。</p>
 *
 * <p><b>剂次不能重复</b>：同一人同一疫苗的同一剂次只允许一条记录，
 * 否则接种提醒会重复催种，还可能被当成漏种补打一针。</p>
 */
@Getter
@Setter
@TableName("biz_vaccination")
public class BizVaccination extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    /** 疫苗名称，取 biz_drug.drug_name（category='疫苗'） */
    private String vaccineName;

    /** 疫苗批号，对应 biz_stock.batch_no */
    private String batchNo;

    /** 剂次，从 1 开始递增 */
    private Integer doseNo;

    private LocalDate vaccinateDate;

    /** 接种人 */
    private Long operatorId;

    /** 下次接种日期，接种提醒据此筛选 */
    private LocalDate nextDate;
}
