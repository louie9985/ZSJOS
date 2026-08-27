package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.*;
import cn.iocoder.yudao.module.system.service.workbenchlayout.WorkbenchLayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - Workbench 菜单编排")
@RestController
@RequestMapping("/system/workbench-layout")
@Validated
public class WorkbenchLayoutController {

    @Resource
    private WorkbenchLayoutService workbenchLayoutService;

    @GetMapping("/candidates")
    @Operation(summary = "获得全局候选页面和租户角色；角色候选页面随草稿接口返回")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:query')")
    public CommonResult<WorkbenchLayoutCandidateRespVO> getCandidates() {
        return success(workbenchLayoutService.getCandidates());
    }

    @GetMapping("/draft")
    @Operation(summary = "获得全局或角色布局草稿")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:query')")
    public CommonResult<WorkbenchLayoutDraftRespVO> getDraft(
            @RequestParam("scopeType") String scopeType,
            @RequestParam("scopeId") Long scopeId) {
        return success(workbenchLayoutService.getDraft(scopeType, scopeId));
    }

    @PutMapping("/draft")
    @Operation(summary = "保存完整可编辑布局草稿")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:update')")
    public CommonResult<Integer> saveDraft(@Valid @RequestBody WorkbenchLayoutSaveReqVO reqVO) {
        return success(workbenchLayoutService.saveDraft(reqVO));
    }

    @PostMapping("/preview")
    @Operation(summary = "按员工账号预览最终 Workbench 导航")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:query')")
    public CommonResult<WorkbenchLayoutPreviewRespVO> preview(
            @Valid @RequestBody WorkbenchLayoutPreviewReqVO reqVO) {
        return success(workbenchLayoutService.preview(reqVO));
    }

    @GetMapping("/publish-impact")
    @Operation(summary = "计算当前草稿的发布影响")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:query')")
    public CommonResult<WorkbenchLayoutImpactRespVO> getPublishImpact(
            @RequestParam("scopeType") String scopeType,
            @RequestParam("scopeId") Long scopeId) {
        return success(workbenchLayoutService.getPublishImpact(scopeType, scopeId));
    }

    @PostMapping("/publish")
    @Operation(summary = "发布全局或单个角色布局")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:publish')")
    public CommonResult<Long> publish(@Valid @RequestBody WorkbenchLayoutPublishReqVO reqVO) {
        return success(workbenchLayoutService.publish(reqVO, getLoginUserId()));
    }

    @GetMapping("/versions")
    @Operation(summary = "获得不可变发布历史")
    @Parameter(name = "scopeType", required = true)
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:query')")
    public CommonResult<List<WorkbenchLayoutVersionRespVO>> getVersions(
            @RequestParam("scopeType") String scopeType,
            @RequestParam("scopeId") Long scopeId) {
        return success(workbenchLayoutService.getVersions(scopeType, scopeId));
    }

    @PostMapping("/restore-draft")
    @Operation(summary = "将历史版本恢复为新草稿")
    @PreAuthorize("@ss.hasPermission('system:workbench-layout:update')")
    public CommonResult<Integer> restoreDraft(@Valid @RequestBody WorkbenchLayoutRestoreReqVO reqVO) {
        return success(workbenchLayoutService.restoreDraft(reqVO));
    }

}
