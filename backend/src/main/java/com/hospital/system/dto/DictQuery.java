package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

/** 字典查询条件。字典量级很小，不分页，但要能按名称/类型过滤与只看启用。 */
@Getter
@Setter
public class DictQuery {

    private String dictName;

    private String dictType;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
