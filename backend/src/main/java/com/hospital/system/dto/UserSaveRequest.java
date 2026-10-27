package com.hospital.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户新增/修改请求。
 *
 * <p>修改时不传 password，改密走独立接口，避免「编辑用户顺手覆盖密码」这类事故。</p>
 */
@Getter
@Setter
public class UserSaveRequest {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,49}$",
            message = "账号需以字母开头，4-50 位字母/数字/下划线")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    /** 0 女 / 1 男 */
    private Integer gender;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过 50")
    private String email;

    private Long deptId;

    private Long roleId;

    /** 0 禁用 / 1 启用，不传默认启用 */
    private Integer status;

    /** 仅新增时生效，不传默认 123456 */
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;
}
