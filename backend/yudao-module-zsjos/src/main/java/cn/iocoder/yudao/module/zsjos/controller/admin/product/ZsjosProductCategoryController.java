package cn.iocoder.yudao.module.zsjos.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ZSJOS 产品分类")
@RestController
@RequestMapping("/zsjos/product/category")
@Validated
public class ZsjosProductCategoryController {
    @Resource private ZsjosProductCategoryService categoryService;

    @PostMapping("/create") @Operation(summary = "创建产品分类")
    @PreAuthorize("@ss.hasPermission('zsjos:product-category:create')")
    public CommonResult<Long> create(@Valid @RequestBody ZsjosProductCategorySaveReqVO reqVO) { return success(categoryService.create(reqVO)); }

    @PutMapping("/update") @Operation(summary = "更新产品分类")
    @PreAuthorize("@ss.hasPermission('zsjos:product-category:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ZsjosProductCategorySaveReqVO reqVO) { categoryService.update(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除产品分类")
    @PreAuthorize("@ss.hasPermission('zsjos:product-category:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) { categoryService.delete(id); return success(true); }

    @PutMapping("/update-status") @Operation(summary = "启用或停用产品分类")
    @PreAuthorize("@ss.hasPermission('zsjos:product-category:status')")
    public CommonResult<Boolean> updateStatus(@RequestParam Long id, @RequestParam Integer status) { categoryService.updateStatus(id, status); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得产品分类")
    @PreAuthorize("@ss.hasPermission('zsjos:product-category:query')")
    public CommonResult<ZsjosProductCategoryRespVO> get(@RequestParam Long id) { return success(categoryService.get(id)); }

    @GetMapping("/tree") @Operation(summary = "获得产品分类树")
    @PreAuthorize("@ss.hasPermission('zsjos:product-category:query')")
    public CommonResult<List<ZsjosProductCategoryRespVO>> tree() { return success(categoryService.getTree()); }
}
