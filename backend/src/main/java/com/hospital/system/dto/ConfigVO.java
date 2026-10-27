package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 系统参数视图。 */
@Getter
@Setter
public class ConfigVO {

    private String id;

    private String configKey;

    private String configValue;

    private String configName;

    /** 1 系统内置（不可删除） / 2 业务参数 */
    private Integer configType;

    private String remark;

    private LocalDateTime updateTime;
}
