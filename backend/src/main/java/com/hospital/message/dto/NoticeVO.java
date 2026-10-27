package com.hospital.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 公告视图对象。
 *
 * <p>同时作为公告联查（公告 + 科室名 + 发布人姓名）的返回类型，
 * 所以必须有无参构造 —— MyBatis 反射建对象只认无参构造。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeVO {

    private String id;
    private String title;
    private String content;

    /** 1 院内 / 2 科室 */
    private Integer noticeType;
    private String noticeTypeName;

    private String deptId;
    private String deptName;

    private String publisherId;
    private String publisherName;

    private LocalDateTime publishTime;

    /** 0 草稿 / 1 已发布 */
    private Integer status;

    private LocalDateTime createTime;
}
