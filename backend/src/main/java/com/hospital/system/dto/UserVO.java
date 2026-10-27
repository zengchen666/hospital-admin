package com.hospital.system.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户列表/详情返回对象。
 *
 * <p>ID 用 String 防前端精度丢失；手机号脱敏输出。</p>
 */
@Getter
@Builder
public class UserVO {

    private final String id;

    private final String username;

    private final String realName;

    private final Integer gender;

    /** 已脱敏，如 138****0000 */
    private final String phone;

    private final String email;

    private final String deptId;

    private final String roleId;

    private final Integer status;

    private final LocalDateTime createTime;
}
