package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 高级筛选模板")
@RestController
@RequestMapping("/zsjos/advanced-filter-template")
@Validated
public class AdvancedFilterTemplateController {
    @Resource private AdvancedFilterTemplateService service;

    // 守卫按 scene 判定，与 AdvancedFilterController.catalog 保持一致：预置模板要落在页面上，前提是该页面
    // 已经能取到同一场景的字段目录，两者的授权范围必须相同。原先的静态权限并集遗漏了场景专属权限
    // （如订单管理的 zsjos:sales-order:query-management），会让有权访问页面的账号取不到预置。
    @GetMapping("/visible-list")
    @Operation(summary = "获得当前页面可用高级筛选模板")
    @PreAuthorize("(#scene == 'lead' && @ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted',"
            + "'zsjos:lead:query-owned','zsjos:lead:claim','zsjos:lead:claim-pool:query',"
            + "'zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query'))"
            + " || (#scene == 'order' && @ss.hasAnyPermissions('zsjos:sales-order:query','zsjos:sales-order:query-management','zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:sales-order:create'))"
            + " || (#scene == 'lead_appeal' && @ss.hasAnyPermissions('zsjos:lead:appeal:query',"
            + "'zsjos:lead:appeal:review-sales-manager','zsjos:lead:appeal:review-quality',"
            + "'zsjos:lead:appeal:review-chairman'))"
            + " || (#scene == 'duplicate_review' && @ss.hasPermission('zsjos:lead-duplicate-review:query'))"
            + " || (#scene == 'registration' && @ss.hasPermission('zsjos:registration:query-pool'))"
            + " || (#scene == 'student' && @ss.hasAnyPermissions('zsjos:student:query-my','zsjos:media-student:query-my'))"
            + " || (#scene == 'subordinate_sales' && @ss.hasPermission('zsjos:subordinate-sales:query'))")
    public CommonResult<List<AdvancedFilterTemplateRespVO>> visibleList(
            @RequestParam @Pattern(regexp = "lead|order|lead_appeal|duplicate_review|registration|student|subordinate_sales") String scene,
            @RequestParam @Pattern(regexp = "[a-z][a-z0-9_:-]{1,95}") String pageKey) {
        return success(service.visibleList(scene, pageKey, getLoginUserId()));
    }

    @PostMapping("/personal")
    @Operation(summary = "创建个人高级筛选模板")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted','zsjos:lead:query-owned',"
            + "'zsjos:lead:claim','zsjos:lead:claim-pool:query','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query','zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:lead:appeal:query',"
            + "'zsjos:lead-duplicate-review:query','zsjos:registration:query-pool','zsjos:student:query-my')")
    public CommonResult<Long> createPersonal(@Valid @RequestBody AdvancedFilterTemplateSaveReqVO reqVO) {
        return success(service.createPersonal(reqVO, getLoginUserId()));
    }

    @PutMapping("/personal")
    @Operation(summary = "修改个人高级筛选模板")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted','zsjos:lead:query-owned',"
            + "'zsjos:lead:claim','zsjos:lead:claim-pool:query','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query','zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:lead:appeal:query',"
            + "'zsjos:lead-duplicate-review:query','zsjos:registration:query-pool','zsjos:student:query-my')")
    public CommonResult<Boolean> updatePersonal(@Valid @RequestBody AdvancedFilterTemplateSaveReqVO reqVO) {
        service.updatePersonal(reqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/personal")
    @Operation(summary = "删除个人高级筛选模板")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted','zsjos:lead:query-owned',"
            + "'zsjos:lead:claim','zsjos:lead:claim-pool:query','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query','zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:lead:appeal:query',"
            + "'zsjos:lead-duplicate-review:query','zsjos:registration:query-pool','zsjos:student:query-my')")
    public CommonResult<Boolean> deletePersonal(@RequestParam Long id) {
        service.deletePersonal(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/system-list")
    @Operation(summary = "获得系统预置高级筛选模板")
    @PreAuthorize("@ss.hasPermission('zsjos:advanced-filter-template:query')")
    public CommonResult<List<AdvancedFilterTemplateRespVO>> systemList(
            @RequestParam @Pattern(regexp = "lead|order|lead_appeal|duplicate_review|registration|student|subordinate_sales") String scene,
            @RequestParam @Pattern(regexp = "[a-z][a-z0-9_:-]{1,95}") String pageKey) {
        return success(service.systemList(scene, pageKey));
    }

    @PostMapping("/system")
    @Operation(summary = "创建系统预置高级筛选模板")
    @PreAuthorize("@ss.hasPermission('zsjos:advanced-filter-template:update')")
    public CommonResult<Long> createSystem(@Valid @RequestBody AdvancedFilterTemplateSaveReqVO reqVO) {
        return success(service.createSystem(reqVO));
    }

    @PutMapping("/system")
    @Operation(summary = "修改系统预置高级筛选模板")
    @PreAuthorize("@ss.hasPermission('zsjos:advanced-filter-template:update')")
    public CommonResult<Boolean> updateSystem(@Valid @RequestBody AdvancedFilterTemplateSaveReqVO reqVO) {
        service.updateSystem(reqVO);
        return success(true);
    }

    @DeleteMapping("/system")
    @Operation(summary = "删除系统预置高级筛选模板")
    @PreAuthorize("@ss.hasPermission('zsjos:advanced-filter-template:update')")
    public CommonResult<Boolean> deleteSystem(@RequestParam Long id) {
        service.deleteSystem(id);
        return success(true);
    }
}
