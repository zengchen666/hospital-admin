package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 字典项视图。
 *
 * <p>下拉框只需要 {@code itemValue / itemLabel} 两列，
 * 但管理页还要 {@code sort / status / cssClass}，因此不做两份 VO ——
 * 一份字段多一点的视图，代价远小于「两个 VO 少写一个字段要改两处」。</p>
 */
@Getter
@Setter
public class DictItemVO {

    private String id;

    private String dictType;

    private String itemLabel;

    private String itemValue;

    private Integer sort;

    private String cssClass;

    /** 0 停用 / 1 启用 */
    private Integer status;
}
