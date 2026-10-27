package com.hospital.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hospital.common.security.CurrentUser;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充：create_by / create_time / update_by / update_time。
 *
 * <p>为什么由后端填充而不是前端传：前端传值可被伪造，审计就失去意义。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";
    private static final String CREATE_BY = "createBy";
    private static final String UPDATE_BY = "updateBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = CurrentUser.idOrNull();

        if (hasSetter(metaObject, CREATE_TIME)) {
            strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        }
        if (hasSetter(metaObject, UPDATE_TIME)) {
            strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        }
        if (userId != null && hasSetter(metaObject, CREATE_BY)) {
            strictInsertFill(metaObject, CREATE_BY, Long.class, userId);
        }
        if (userId != null && hasSetter(metaObject, UPDATE_BY)) {
            strictInsertFill(metaObject, UPDATE_BY, Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = CurrentUser.idOrNull();

        // 更新时不依赖实体字段是否为空，强制覆盖，避免脏值
        if (hasSetter(metaObject, UPDATE_TIME)) {
            setFieldValByName(UPDATE_TIME, LocalDateTime.now(), metaObject);
        }
        if (userId != null && hasSetter(metaObject, UPDATE_BY)) {
            setFieldValByName(UPDATE_BY, userId, metaObject);
        }
    }

    private boolean hasSetter(MetaObject metaObject, String fieldName) {
        return metaObject.hasSetter(fieldName);
    }
}
