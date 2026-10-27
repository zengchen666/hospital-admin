package com.hospital.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解：标注在需要留痕的接口方法上。
 *
 * <p>只标注「写」操作（增删改、解密、导出），查询不打标，否则日志表会被读操作淹没。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 模块名，如「用户管理」 */
    String module();

    /** 操作描述，如「新增用户」 */
    String operation();
}
