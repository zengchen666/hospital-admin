package com.hospital.message.dto;

import com.hospital.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 公告分页查询。 */
@Getter
@Setter
public class NoticeQuery extends PageQuery {

    /** 1 院内 / 2 科室 */
    private Integer noticeType;

    /** 0 草稿 / 1 已发布 */
    private Integer status;
}
