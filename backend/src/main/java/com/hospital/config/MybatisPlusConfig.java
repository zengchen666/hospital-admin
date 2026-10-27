package com.hospital.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件链。
 *
 * <p>顺序有讲究：分页必须在乐观锁之前，否则分页 SQL 会先被改写导致 count 语句异常。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限 500，防止前端传 pageSize=999999 拖垮数据库
        pagination.setMaxLimit(500L);
        // 页码超出总页数时返回空列表，而不是回到第一页（避免静默错误）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // @Version 乐观锁：更新语句自动追加 AND version = ?
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 拦截没有 where 条件的 update/delete，防止误操作全表
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
