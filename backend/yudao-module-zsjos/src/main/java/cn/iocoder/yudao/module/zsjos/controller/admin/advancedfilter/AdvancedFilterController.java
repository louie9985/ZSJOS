package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 客资订单高级筛选")
@RestController
@RequestMapping("/zsjos/advanced-filter")
public class AdvancedFilterController {
    @Resource private AdvancedFilterService service;

    @GetMapping("/catalog")
    @Operation(summary = "获得高级筛选字段目录")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:sales-order:query-own','zsjos:sales-order:review')")
    public CommonResult<AdvancedFilterCatalogRespVO> catalog(@RequestParam String scene) {
        return success(service.catalog(scene));
    }
}
