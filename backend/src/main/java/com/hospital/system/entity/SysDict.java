package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据字典 sys_dict。
 *
 * <p>{@code dict_type} 是业务代码里引用的键，<b>一经使用就不能改</b>：
 * 字典项散落在各种下拉框与校验逻辑里，改了 type 等于把所有引用一次性弄断，
 * 而且不会有任何编译错误提醒你。因此改字典只允许改名称与备注，
 * type 的变更必须连带改代码一起发版。</p>
 */
@Getter
@Setter
@TableName("sys_dict")
public class SysDict extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 字典类型编码，如 gender / pay_type */
    private String dictType;

    private String dictName;

    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
