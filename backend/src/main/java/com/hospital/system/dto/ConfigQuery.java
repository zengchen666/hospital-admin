package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

/** 系统参数查询条件。参数量级很小，不分页。 */
@Getter
@Setter
public class ConfigQuery {

    private String configName;

    private String configKey;

    /** 1 系统内置 / 2 业务参数 */
    private Integer configType;
}
