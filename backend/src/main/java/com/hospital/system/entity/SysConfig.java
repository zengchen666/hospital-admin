package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数 sys_config。
 *
 * <p>{@code config_type} 区分两类参数：</p>
 * <ul>
 *   <li><b>1 系统内置</b>：程序启动就要读的（会话超时、密码策略），
 *       删掉会让系统起不来，因此<b>不允许删除</b>；</li>
 *   <li><b>2 业务参数</b>：医院自己配的（上班时间、预约提前天数），可增删。</li>
 * </ul>
 *
 * <p>参数值一律按字符串存，读取方自己转类型 ——
 * 存成强类型会让「改一个参数的取值范围」变成一次表结构变更。</p>
 */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String configKey;

    private String configValue;

    private String configName;

    /** 1 系统内置（不可删除） / 2 业务参数 */
    private Integer configType;

    private String remark;
}
