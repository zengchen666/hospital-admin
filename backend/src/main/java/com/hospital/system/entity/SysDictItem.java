package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典项 sys_dict_item。
 *
 * <p>{@code item_value} 是落库的值，{@code item_label} 是给人看的文字。
 * 两者必须分开：<b>字典项改名不能改变历史数据的含义</b> ——
 * 把「已婚」改成「已婚（初婚）」，存了 1 的那些记录仍然是同一个值，
 * 界面上换了说法而已。反过来如果把 label 当 value 存，
 * 一次改名就等于把历史数据全量改写。</p>
 */
@Getter
@Setter
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String dictType;

    private String itemLabel;

    private String itemValue;

    private Integer sort;

    private String cssClass;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
