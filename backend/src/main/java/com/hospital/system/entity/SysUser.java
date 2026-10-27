package com.hospital.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户表 sys_user。
 *
 * <p>注意：phone 在库中是 AES-256-GCM 密文，phone_hash 用于精确检索。
 * 加解密由 MyBatis TypeHandler 统一处理（后续接入），业务层不要自行加解密。</p>
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /** BCrypt 密文，格式 {bcrypt}$2a$10$...；兼容历史 {noop} 明文 */
    private String password;

    private String realName;

    /** 0 女 / 1 男 */
    private Integer gender;

    private String phone;

    private String phoneHash;

    private String email;

    private Long deptId;

    /** 主角色ID，多角色见 sys_user_role */
    private Long roleId;

    private String avatar;

    /** 0 禁用 / 1 启用 */
    private Integer status;

    /**
     * 令牌版本号：改密、禁用、强制下线时递增。
     *
     * <p>与父类的 {@code @Version}（乐观锁）<b>刻意分开</b>：
     * 复用同一个字段会导致「管理员改个手机号，用户就被踢下线」，
     * 也会让并发编辑冲突检测误报。</p>
     */
    private Integer tokenVersion;
}
