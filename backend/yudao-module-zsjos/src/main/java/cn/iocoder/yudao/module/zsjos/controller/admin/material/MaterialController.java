package cn.iocoder.yudao.module.zsjos.controller.admin.material;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadCompleteReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialDisableReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialInteractionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferencePreviewReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferencePreviewRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceTargetPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialRecommendationAccountRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialRestoreReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialVersionRespVO;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialInteractionService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialService;
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
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.constraints.Size;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 素材库")
@RestController
@RequestMapping("/zsjos/material")
@Validated
public class MaterialController {

    @Resource
    private MaterialService materialService;
    @Resource
    private MaterialInteractionService interactionService;

    @GetMapping("/page")
    @Operation(summary = "获得素材分页")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:query','zsjos:material:manage')")
    public CommonResult<PageResult<MaterialRespVO>> getPage(@Valid MaterialPageReqVO request) {
        return success(materialService.getPage(request, getLoginUserId()));
    }

    @GetMapping("/recommendation-account-candidates")
    @Operation(summary = "获得当前用户可见的素材推荐账号候选")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:query','zsjos:material:manage')")
    public CommonResult<List<MaterialRecommendationAccountRespVO>> getRecommendationAccountCandidates(
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return success(materialService.getRecommendationAccountCandidates(keyword, getLoginUserId()));
    }

    @GetMapping("/reference-target-candidates")
    @Operation(summary = "获得当前用户可引用的生产内容草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:material:reference')")
    public CommonResult<PageResult<MaterialReferenceTargetRespVO>> getReferenceTargetCandidates(
            @Valid MaterialReferenceTargetPageReqVO request) {
        return success(materialService.getReferenceTargetCandidates(request, getLoginUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得素材详情")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:query','zsjos:material:manage')")
    public CommonResult<MaterialRespVO> get(@PathVariable("id") Long id) {
        return success(materialService.get(id, getLoginUserId()));
    }

    @GetMapping("/{id}/version/list")
    @Operation(summary = "获得素材内容版本")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:query','zsjos:material:manage')")
    public CommonResult<List<MaterialVersionRespVO>> getVersions(@PathVariable("id") Long id) {
        return success(materialService.getVersions(id, getLoginUserId()));
    }

    @GetMapping("/version/{versionId}")
    @Operation(summary = "获得指定素材内容版本")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:query','zsjos:material:manage')")
    public CommonResult<MaterialVersionRespVO> getVersion(@PathVariable("versionId") Long versionId) {
        return success(materialService.getVersion(versionId, getLoginUserId()));
    }

    @PostMapping
    @Operation(summary = "创建素材草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:material:create')")
    public CommonResult<Long> create(@Valid @RequestBody MaterialSaveReqVO request) {
        return success(materialService.create(request, getLoginUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "保存素材草稿或创建修订版本")
    @PreAuthorize("@ss.hasPermission('zsjos:material:update')")
    public CommonResult<Long> update(@PathVariable("id") Long id,
                                     @Valid @RequestBody MaterialSaveReqVO request) {
        return success(materialService.update(id, request, getLoginUserId()));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交素材审批")
    @PreAuthorize("@ss.hasPermission('zsjos:material:submit')")
    public CommonResult<Boolean> submit(@PathVariable("id") Long id,
                                        @Valid @RequestBody MaterialSubmitReqVO request) {
        materialService.submit(id, request, getLoginUserId());
        return success(true);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "停用素材")
    @PreAuthorize("@ss.hasPermission('zsjos:material:disable')")
    public CommonResult<Boolean> disable(@PathVariable("id") Long id,
                                         @Valid @RequestBody MaterialDisableReqVO request) {
        materialService.disable(id, request.getExpectedVersion(), request.getReason(), getLoginUserId());
        return success(true);
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "恢复素材")
    @PreAuthorize("@ss.hasPermission('zsjos:material:restore')")
    public CommonResult<Boolean> restore(@PathVariable("id") Long id,
                                         @Valid @RequestBody MaterialRestoreReqVO request) {
        materialService.restore(id, request.getExpectedVersion());
        return success(true);
    }

    @PutMapping("/{id}/like")
    @Operation(summary = "切换素材点赞状态")
    @PreAuthorize("@ss.hasPermission('zsjos:material:like')")
    public CommonResult<MaterialInteractionRespVO> toggleLike(@PathVariable("id") Long id) {
        return success(interactionService.toggleLike(id, getLoginUserId()));
    }

    @PutMapping("/{id}/favorite")
    @Operation(summary = "切换素材收藏状态")
    @PreAuthorize("@ss.hasPermission('zsjos:material:favorite')")
    public CommonResult<MaterialInteractionRespVO> toggleFavorite(@PathVariable("id") Long id) {
        return success(interactionService.toggleFavorite(id, getLoginUserId()));
    }

    @PostMapping("/{id}/version/{versionId}/reference/preview")
    @Operation(summary = "预览素材字段引用结果")
    @PreAuthorize("@ss.hasPermission('zsjos:material:reference')")
    public CommonResult<MaterialReferencePreviewRespVO> previewReference(
            @PathVariable("id") Long id, @PathVariable("versionId") Long versionId,
            @Valid @RequestBody MaterialReferencePreviewReqVO request) {
        return success(interactionService.previewReference(id, versionId, request, getLoginUserId()));
    }

    @PostMapping("/{id}/version/{versionId}/reference")
    @Operation(summary = "引用素材字段到生产内容草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:material:reference')")
    public CommonResult<Boolean> reference(@PathVariable("id") Long id,
                                           @PathVariable("versionId") Long versionId,
                                           @Valid @RequestBody MaterialReferenceReqVO request) {
        interactionService.reference(id, versionId, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/upload/init")
    @Operation(summary = "初始化素材文件直传")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:create','zsjos:material:update')")
    @ApiAccessLog(responseEnable = true, sanitizeKeys = "uploadToken")
    public CommonResult<ZsjosDirectUploadInitRespVO> initUpload(
            @Valid @RequestBody ZsjosDirectUploadInitReqVO request) {
        return success(materialService.initUpload(request, getLoginUserId()));
    }

    @PostMapping("/upload/complete")
    @Operation(summary = "完成素材文件直传")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:material:create','zsjos:material:update')")
    @ApiAccessLog(sanitizeKeys = "uploadToken")
    public CommonResult<MaterialUploadRespVO> completeUpload(
            @Valid @RequestBody ZsjosDirectUploadCompleteReqVO request) {
        return success(materialService.completeUpload(request.getUploadToken(), getLoginUserId()));
    }
}
