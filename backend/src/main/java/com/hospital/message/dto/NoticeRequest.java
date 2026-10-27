package com.hospital.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 公告新增 / 修改入参。
 *
 * <p>{@code noticeType=2}（科室公告）时 {@code deptId} 必填 ——
 * 没写科室的科室公告等于全院都看不到，也无从判断谁该看到。</p>
 */
@Getter
@Setter
public class NoticeRequest {

    @NotBlank(message = "公告标题不能为空")
    private String title;

    private String content;

    /** 1 院内 / 2 科室 */
    @NotNull(message = "公告类型不能为空")
    private Integer noticeType;

    /** 科室 ID，notice_type=2 时必填 */
    private Long deptId;
}
