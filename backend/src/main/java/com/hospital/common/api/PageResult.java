package com.hospital.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页返回结构，对应文档 1.2：{@code { total, pageNum, pageSize, list }}。
 */
@Getter
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long total;
    private final long pageNum;
    private final long pageSize;
    private final List<T> list;

    private PageResult(long total, long pageNum, long pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list == null ? Collections.emptyList() : list;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /** 实体分页转 VO 分页，避免把 entity 直接吐给前端。 */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> converter) {
        List<V> rows = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), rows);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, Collections.emptyList());
    }
}
