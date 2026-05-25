package org.moyu.rag.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果。
 * <p>配合 {@link Result} 使用：{@code Result<PageResult<T>>}。</p>
 *
 * <pre>{@code
 * return Result.ok(new PageResult<>(page.getCurrent(), page.getSize(),
 *         page.getTotal(), page.getPages(), page.getRecords()));
 * }</pre>
 *
 * @param <T> 记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页码，从 1 开始 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> records;
}
