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

    @GetMapping("/visible-list")
    @Operation(summary = "获得当前页面可用高级筛选模板")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted','zsjos:lead:query-owned',"
            + "'zsjos:lead:claim','zsjos:lead:claim-pool:query','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:lead:appeal:query',"
            + "'zsjos:lead-duplicate-review:query','zsjos:registration:query-pool','zsjos:student:query-my')")
    public CommonResult<List<AdvancedFilterTemplateRespVO>> visibleList(
            @RequestParam @Pattern(regexp = "lead|order|lead_appeal|duplicate_review|registration|student|subordinate_sales") String scene,
            @RequestParam @Pattern(regexp = "[a-z][a-z0-9_:-]{1,95}") String pageKey) {
        return success(service.visibleList(scene, pageKey, getLoginUserId()));
    }

    @PostMapping("/personal")
    @Operation(summary = "创建个人高级筛选模板")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted','zsjos:lead:query-owned',"
            + "'zsjos:lead:claim','zsjos:lead:claim-pool:query','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query-own',"
            + "'zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:lead:appeal:query',"
            + "'zsjos:lead-duplicate-review:query','zsjos:registration:query-pool','zsjos:student:query-my')")
    public CommonResult<Long> createPersonal(@Valid @RequestBody AdvancedFilterTemplateSaveReqVO reqVO) {
        return success(service.createPersonal(reqVO, getLoginUserId()));
    }

    @PutMapping("/personal")
    @Operation(summary = "修改个人高级筛选模板")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:lead:query-submitted','zsjos:lead:query-owned',"
            + "'zsjos:lead:claim','zsjos:lead:claim-pool:query','zsjos:lead:query-all','zsjos:lead-aging-pool:query',"
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query-own',"
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
            + "'zsjos:lead:qualification:query','zsjos:subordinate-sales:query','zsjos:sales-order:query-own',"
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
