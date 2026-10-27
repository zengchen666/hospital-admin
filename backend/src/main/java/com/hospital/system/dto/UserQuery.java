package com.hospital.system.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserQuery extends PageQuery {

    private Long deptId;

    private Long roleId;

    /** 0 禁用 / 1 启用 */
    private Integer status;
}
