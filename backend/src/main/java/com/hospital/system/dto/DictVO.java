package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典视图（含字典项）。
 *
 * <p>字典与字典项一起返回：前端字典页本质是在维护一张「类型 → 若干取值」的表，
 * 分开两个接口就会出现「先拉字典再拉字典项，中间字典被删了」的空指针。</p>
 */
@Getter
@Setter
public class DictVO {

    private String id;

    private String dictType;

    private String dictName;

    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status;

    private List<DictItemVO> items;

    private LocalDateTime createTime;
}
