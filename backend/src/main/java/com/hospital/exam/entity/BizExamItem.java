package com.hospital.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验项目明细 biz_exam_item。
 *
 * <p><b>一条记录同时承载「申请项」与「结果项」</b>：开单时只写编码与名称，
 * 录入报告时回填结果值、参考范围与异常标志。
 * 不做成两张表是因为它们本来就是一回事的不同阶段，拆开反而要维护映射关系。</p>
 */
@Getter
@Setter
@TableName("biz_exam_item")
public class BizExamItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applyId;

    /** 项目编码，如 WBC */
    private String itemCode;

    /** 项目名称，如 白细胞计数 */
    private String itemName;

    /** 结果值（12.5 / 阴性），开单时为空 */
    private String resultValue;

    /** 参考范围，如 4-10 */
    private String refRange;

    private String unit;

    /** 异常标志：0 正常 / 1 偏高 / 2 偏低 / 3 定性异常 */
    private Integer abnormalFlag;

    /** 排序，按报告模板展示 */
    private Integer sort;
}
