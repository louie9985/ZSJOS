package cn.iocoder.yudao.module.zsjos.controller.admin.material;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSchemaSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialProcessDefinitionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTemplatePublishReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTemplateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTypeRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTypeSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 素材类型与模板")
@RestController
@RequestMapping("/zsjos/material-type")
@Validated
public class MaterialTypeController {

    @Resource private MaterialTypeService materialTypeService;

    @GetMapping("/list")
    @Operation(summary = "获得素材类型列表")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material-type:query','zsjos:material:query','zsjos:material:manage','zsjos:material:create')")
    public CommonResult<List<MaterialTypeRespVO>> getTypeList() {
        return success(materialTypeService.getTypeList());
    }

    @GetMapping("/process-definition/list")
    @Operation(summary = "获得已发布的素材审批流程")
    @PreAuthorize("@ss.hasPermission('zsjos:material-type:query')")
    public CommonResult<List<MaterialProcessDefinitionRespVO>> getProcessDefinitionList() {
        return success(materialTypeService.getPublishedProcessDefinitions());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得素材类型")
    @PreAuthorize("@ss.hasPermission('zsjos:material-type:query')")
    public CommonResult<MaterialTypeRespVO> getType(@PathVariable("id") Long id) {
        return success(materialTypeService.getType(id));
    }

    @PostMapping
    @Operation(summary = "创建素材类型")
    @PreAuthorize("@ss.hasPermission('zsjos:material-type:create')")
    public CommonResult<Long> createType(@Valid @RequestBody MaterialTypeSaveReqVO request) {
        return success(materialTypeService.createType(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改素材类型")
    @PreAuthorize("@ss.hasPermission('zsjos:material-type:update')")
    public CommonResult<Boolean> updateType(@PathVariable("id") Long id,
                                            @Valid @RequestBody MaterialTypeSaveReqVO request) {
        materialTypeService.updateType(id, request);
        return success(true);
    }

    @GetMapping("/{id}/schema/list")
    @Operation(summary = "获得素材模板版本")
    @PreAuthorize("@ss.hasPermission('zsjos:material-type:query')")
    public CommonResult<List<MaterialTemplateRespVO>> getSchemaVersions(@PathVariable("id") Long id) {
        return success(materialTypeService.getSchemaVersions(id));
    }

    @PutMapping("/{id}/schema/draft")
    @Operation(summary = "保存素材模板草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:material-schema:update')")
    public CommonResult<Long> saveSchemaDraft(@PathVariable("id") Long id,
                                              @Valid @RequestBody MaterialSchemaSaveReqVO request) {
        return success(materialTypeService.saveSchemaDraft(id, request));
    }

    @PostMapping("/{id}/schema/publish")
    @Operation(summary = "发布素材模板")
    @PreAuthorize("@ss.hasPermission('zsjos:material-schema:publish')")
    public CommonResult<Boolean> publishSchema(@PathVariable("id") Long id,
                                               @Valid @RequestBody MaterialTemplatePublishReqVO request) {
        materialTypeService.publishSchema(id, request, getLoginUserId());
        return success(true);
    }
}
