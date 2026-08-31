package cn.iocoder.yudao.module.zsjos.controller.admin.product;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ZSJOS 产品配置")
@RestController
@RequestMapping("/zsjos/product")
@Validated
public class ZsjosProductController {
    @Resource private ZsjosProductService productService;

    @PostMapping("/create")
    @Operation(summary = "创建产品")
    @PreAuthorize("@ss.hasPermission('zsjos:product:create')")
    public CommonResult<Long> create(@Valid @RequestBody ZsjosProductSaveReqVO reqVO) {
        return success(productService.createProduct(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新产品")
    @PreAuthorize("@ss.hasPermission('zsjos:product:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ZsjosProductSaveReqVO reqVO) {
        productService.updateProduct(reqVO); return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除产品")
    @PreAuthorize("@ss.hasPermission('zsjos:product:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        productService.deleteProduct(id); return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用或停用产品")
    @PreAuthorize("@ss.hasPermission('zsjos:product:status')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ZsjosProductStatusReqVO reqVO) {
        productService.updateStatus(reqVO); return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得产品")
    @PreAuthorize("@ss.hasPermission('zsjos:product:query')")
    public CommonResult<ZsjosProductRespVO> get(@RequestParam Long id) { return success(productService.getProduct(id)); }

    @GetMapping("/page")
    @Operation(summary = "获得产品分页")
    @PreAuthorize("@ss.hasPermission('zsjos:product:query')")
    public CommonResult<PageResult<ZsjosProductRespVO>> page(@Valid ZsjosProductPageReqVO reqVO) {
        return success(productService.getProductPage(reqVO));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用产品列表")
    @PreAuthorize("@ss.hasPermission('zsjos:product:query')")
    public CommonResult<List<ZsjosProductSimpleRespVO>> simpleList() { return success(productService.getEnabledSimpleList()); }

    @PostMapping("/validate")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @Operation(summary = "批量校验启用产品")
    @PreAuthorize("@ss.hasPermission('zsjos:product:query')")
    public CommonResult<List<ZsjosProductValidateRespVO>> validate(@Valid @RequestBody ZsjosProductValidateReqVO reqVO) {
        return success(productService.validateEnabledProducts(reqVO.getProductRefs()).stream()
                .map(item -> new ZsjosProductValidateRespVO(item.productRef(), item.name(), item.categoryId(),
                        item.categoryName(), item.categoryPath(),
                        item.level1CategoryId(), item.level1CategoryName(),
                        item.level2CategoryId(), item.level2CategoryName())).toList());
    }
}
