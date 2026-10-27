package com.hospital.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表数据快照 sys_report_data（预聚合结果）。
 *
 * <p>不继承 {@code BaseEntity}：快照是「某一天算出来的一份结果」，
 * 没有逻辑删除与乐观锁 —— 重复生成同一天应该是覆盖（见 Mapper 的
 * {@code ON DUPLICATE KEY UPDATE}），而不是报版本冲突。</p>
 *
 * <p><b>为什么需要它</b>：明细表会持续增长，今天跑得动的聚合 SQL 两年后
 * 未必跑得动；而「去年 3 月的经营日报」永远应该是同一个数字，不该因为
 * 后来有人退了一笔费就悄悄变化。快照把历史口径钉死。</p>
 */
@Getter
@Setter
@TableName("sys_report_data")
public class SysReportData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long reportId;

    private LocalDate statDate;

    private String dataJson;

    private LocalDateTime createTime;
}
