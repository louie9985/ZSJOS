package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterVisibleUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客资订单高级筛选")
@RestController
@RequestMapping("/zsjos/advanced-filter")
public class AdvancedFilterController {
    @Resource private AdvancedFilterService service;
    @Resource private AdvancedFilterVisibleUserService visibleUserService;

    @GetMapping("/catalog")
    @Operation(summary = "获得高级筛选字段目录")
    @PreAuthorize("(#scene == 'lead' && @ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted',"
            + "'zsjos:lead:query-owned','zsjos:lead:claim','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query'))"
            + " || (#scene == 'order' && @ss.hasAnyPermissions('zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm'))"
            + " || (#scene == 'lead_appeal' && @ss.hasAnyPermissions('zsjos:lead:appeal:query',"
            + "'zsjos:lead:appeal:review-sales-manager','zsjos:lead:appeal:review-quality',"
            + "'zsjos:lead:appeal:review-chairman'))"
            + " || (#scene == 'duplicate_review' && @ss.hasPermission('zsjos:lead-duplicate-review:query'))"
            + " || (#scene == 'registration' && @ss.hasPermission('zsjos:registration:query-pool'))"
            + " || (#scene == 'student' && @ss.hasPermission('zsjos:student:query-my'))"
            + " || (#scene == 'subordinate_sales' && @ss.hasPermission('zsjos:subordinate-sales:query'))")
    public CommonResult<AdvancedFilterCatalogRespVO> catalog(@RequestParam String scene) {
        var userScope = visibleUserService.resolve(scene, getLoginUserId());
        return success(userScope.supported()
                ? service.catalog(scene, userScope.options())
                : service.catalogWithoutVisibleUsers(scene));
    }
}
