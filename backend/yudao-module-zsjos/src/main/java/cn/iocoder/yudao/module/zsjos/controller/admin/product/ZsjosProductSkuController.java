package cn.iocoder.yudao.module.zsjos.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.*;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ZSJOS 课程 SKU")
@RestController
@RequestMapping("/zsjos/product/sku")
public class ZsjosProductSkuController {
    @Resource private ZsjosProductSkuService skuService;

    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-create')")
    public CommonResult<Long> create(@Valid @RequestBody ZsjosProductSkuSaveReqVO reqVO) { return success(skuService.createSku(reqVO)); }
    @PostMapping("/generate") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-create')")
    public CommonResult<Integer> generate(@RequestParam Long spuId) { return success(skuService.generateSkus(spuId)); }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ZsjosProductSkuSaveReqVO reqVO) { skuService.updateSku(reqVO); return success(true); }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) { skuService.deleteSku(id); return success(true); }
    @PutMapping("/update-status") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-status')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ZsjosProductSkuStatusReqVO reqVO) { skuService.updateSkuStatus(reqVO); return success(true); }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-query')")
    public CommonResult<ZsjosProductSkuRespVO> get(@RequestParam Long id) { return success(skuService.getSku(id)); }
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('zsjos:product:sku-query')")
    public CommonResult<List<ZsjosProductSkuRespVO>> list(@RequestParam Long spuId) { return success(skuService.getSkuList(spuId)); }
    @GetMapping("/attrs") @PreAuthorize("@ss.hasPermission('zsjos:product:attr-query')")
    public CommonResult<List<ZsjosProductAttrRespVO>> attrs(@RequestParam Long spuId) { return success(skuService.getAttrs(spuId)); }
    @PutMapping("/attrs") @Operation(summary = "保存 SPU 销售属性")
    @PreAuthorize("@ss.hasPermission('zsjos:product:attr-update')")
    public CommonResult<Boolean> saveAttrs(@Valid @RequestBody ZsjosProductAttrSaveReqVO reqVO) { skuService.saveAttrs(reqVO); return success(true); }
}
