package cn.iocoder.yudao.module.zsjos.controller.admin.workplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.*;
import cn.iocoder.yudao.module.zsjos.service.workplan.WorkPlanTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 工作计划模板配置")
@RestController
@RequestMapping("/zsjos/work-plan-config")
public class WorkPlanTemplateController {
    @Resource private WorkPlanTemplateService service;

    @GetMapping("/types") @Operation(summary = "获得计划类型")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:query')")
    public CommonResult<List<WorkPlanTypeRespVO>> types() { return success(service.getTypes()); }

    @PostMapping("/types") @Operation(summary = "创建计划类型")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:create')")
    public CommonResult<Long> createType(@Valid @RequestBody WorkPlanTypeSaveReqVO reqVO) { return success(service.createType(reqVO)); }

    @PutMapping("/types/{id}") @Operation(summary = "修改计划类型")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:update')")
    public CommonResult<Boolean> updateType(@PathVariable Long id, @Valid @RequestBody WorkPlanTypeSaveReqVO reqVO) { service.updateType(id, reqVO); return success(true); }

    @GetMapping("/templates") @Operation(summary = "获得计划模板")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:query')")
    public CommonResult<List<WorkPlanTemplateRespVO>> templates(@RequestParam(required = false) Long typeId) { return success(service.getTemplates(typeId)); }

    @GetMapping("/templates/{id}") @Operation(summary = "获得计划模板详情")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:query')")
    public CommonResult<WorkPlanTemplateRespVO> template(@PathVariable Long id) { return success(service.getTemplate(id)); }

    @PostMapping("/templates") @Operation(summary = "创建计划模板版本")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:create')")
    public CommonResult<Long> createTemplate(@Valid @RequestBody WorkPlanTemplateSaveReqVO reqVO) { return success(service.createTemplate(reqVO, getLoginUserId())); }

    @PutMapping("/templates/{id}") @Operation(summary = "修改计划模板草稿版本")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:update')")
    public CommonResult<Boolean> updateTemplate(@PathVariable Long id, @Valid @RequestBody WorkPlanTemplateSaveReqVO reqVO) { service.updateTemplate(id, reqVO, getLoginUserId()); return success(true); }

    @PostMapping("/templates/{id}/versions/copy") @Operation(summary = "复制模板版本为新草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:update')")
    public CommonResult<Long> copyTemplateVersion(@PathVariable Long id) { return success(service.copyTemplateVersion(id, getLoginUserId())); }

    @PostMapping("/templates/{id}/publish") @Operation(summary = "发布计划模板")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:publish')")
    public CommonResult<Boolean> publishTemplate(@PathVariable Long id) { service.publishTemplate(id, getLoginUserId()); return success(true); }

    @PostMapping("/templates/{id}/disable") @Operation(summary = "停用计划模板")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan-config:disable')")
    public CommonResult<Boolean> disableTemplate(@PathVariable Long id) { service.disableTemplate(id, getLoginUserId()); return success(true); }
}
