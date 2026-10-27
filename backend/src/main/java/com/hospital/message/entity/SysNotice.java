package com.hospital.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 公告 sys_notice。
 *
 * <p>状态：<b>0 草稿 → 1 已发布</b>。</p>
 *
 * <p><b>已发布的公告不可再改</b>：公告是给全院/科室看的通知，
 * 发出去之后悄悄改内容，看到旧版的人就和新版对不上，出了问题扯不清。
 * 要改只能删除后重新发布一条（删除是逻辑删除，且只有草稿或未发布才允许）。</p>
 */
@Getter
@Setter
@TableName("sys_notice")
public class SysNotice extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String content;

    /** 1 院内 / 2 科室 */
    private Integer noticeType;

    /** 科室 ID，notice_type=2 时必填 */
    private Long deptId;

    private Long publisherId;

    private LocalDateTime publishTime;

    /** 0 草稿 / 1 已发布 */
    private Integer status;
}
