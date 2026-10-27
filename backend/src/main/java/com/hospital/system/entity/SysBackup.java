package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据备份记录 sys_backup。
 *
 * <p>不继承 {@code BaseEntity}：备份记录是「某一次备份行为留下的凭据」，
 * 逻辑删除会让人以为「删了记录备份文件也就没了」—— 实际上文件还在磁盘上，
 * 而那条能证明它存在过的记录没了。</p>
 *
 * <p><b>这里只记录，不承载文件本身</b>。文件放磁盘，数据库只存路径与大小，
 * 避免把几十 MB 的二进制塞进表里拖垮整个库。</p>
 */
@Getter
@Setter
@TableName("sys_backup")
public class SysBackup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String backupName;

    private String backupPath;

    private Long backupSize;

    /** 1 全量 / 2 增量 / 3 手动 */
    private Integer backupType;

    /** 0 失败 / 1 成功 */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;
}
