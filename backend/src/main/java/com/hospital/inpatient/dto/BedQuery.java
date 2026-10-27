package com.hospital.inpatient.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 床位查询条件。
 */
@Getter
@Setter
public class BedQuery extends PageQuery {

    private Long wardId;

    /** 0 空闲 / 1 占用 / 2 维修 */
    private Integer status;

    private Integer bedType;

    /** true 时只看空闲床位（分床弹窗用） */
    private Boolean onlyAvailable;
}
