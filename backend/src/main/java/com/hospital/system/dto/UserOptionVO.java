package com.hospital.system.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户下拉选项。
 *
 * <p>只带「选人」需要的字段：排班选医生、医嘱选执行人都是这个用途。
 * 手机号、邮箱、状态一律不下发 —— 下拉框不需要的东西给了，
 * 迟早会被拿去做别的用途。</p>
 *
 * <p>由 Service 从 {@code SysUser} 映射而来，不直接作为 MyBatis 返回类型，
 * 所以不需要无参构造。</p>
 */
@Getter
@Setter
@Builder
public class UserOptionVO {

    private String id;

    private String realName;

    private String deptId;
}
