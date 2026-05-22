package com.example.demo.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用 CRUD Controller。
 *
 * <p>子 Controller 继承后自动获得分页查询、查单个、新增、修改、删除、全量列表 6 个接口。
 * 不需要这些接口的实体，在子 Controller 中覆盖对应方法即可。</p>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/tags")
 * public class TagController extends BaseController<TagService, Tag> {
 *     // 6 个接口自动继承，一行不写
 * }
 * }</pre>
 *
 * @param <S> Service 类型（需继承 {@link IService}）
 * @param <T> 实体类型
 */
public abstract class BaseController<S extends IService<T>, T> {

    @Autowired
    protected S service;

    // ==================== 分页查询 ====================

    @GetMapping("/page")
    public Result<PageResult<T>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        IPage<T> page = service.page(new Page<>(current, size));
        return Result.ok(new PageResult<>(
                page.getCurrent(), page.getSize(),
                page.getTotal(), page.getPages(), page.getRecords()));
    }

    // ==================== 查单个 ====================

    @GetMapping("/{id}")
    public Result<T> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    // ==================== 新增 ====================

    @PostMapping
    public Result<Void> save(@Valid @RequestBody T entity) {
        service.save(entity);
        return Result.ok();
    }

    // ==================== 修改 ====================

    @PutMapping
    public Result<Void> update(@Valid @RequestBody T entity) {
        service.updateById(entity);
        return Result.ok();
    }

    // ==================== 删除 ====================

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.ok();
    }

    // ==================== 全量列表 ====================

    @GetMapping("/list")
    public Result<List<T>> list() {
        return Result.ok(service.list());
    }
}
