package com.hospital.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 备份记录视图。
 *
 * <p>{@code sizeText} 由后端算好：备份大小是给人看的数字，
 * 前端再写一遍单位换算就会出现「3.5 MB」和「3670016」两种显示。</p>
 */
@Getter
@Setter
public class BackupVO {

    private String id;

    private String backupName;

    /** 服务端文件路径不返回给前端：它属于服务器布局信息，且可用于路径探测 */
    private Long backupSize;

    /** 1 全量 / 2 增量 / 3 手动 */
    private Integer backupType;

    /** 0 失败 / 1 成功 */
    private Integer status;

    private String remark;

    private String sizeText;

    private LocalDateTime createTime;
}
