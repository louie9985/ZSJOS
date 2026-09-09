package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCandidatePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCandidateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCompleteReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewProcessDefinitionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewPublishReqVO;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewBatchService;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewConfigService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 生产内容批审")
@RestController
@RequestMapping("/zsjos/content-review")
@Validated
public class ContentReviewController {

    @Resource private ContentReviewBatchService batchService;
    @Resource private ContentReviewConfigService configService;

    @GetMapping("/config")
    @Operation(summary = "获得生产内容批审配置")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:config')")
    public CommonResult<ContentReviewConfigRespVO> getConfig() {
        return success(configService.getConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "更新生产内容批审配置")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:config')")
    public CommonResult<Boolean> updateConfig(@Valid @RequestBody ContentReviewConfigSaveReqVO request) {
        configService.updateConfig(request, getLoginUserId());
        return success(true);
    }

    @GetMapping("/process-definition/list")
    @Operation(summary = "获得可用的生产内容批审流程")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:config')")
    public CommonResult<List<ContentReviewProcessDefinitionRespVO>> getProcessDefinitions() {
        return success(configService.getProcessDefinitions());
    }

    @PostMapping("/batch/create")
    @Operation(summary = "创建生产内容审核批次")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:create')")
    public CommonResult<Long> create(@Valid @RequestBody ContentReviewBatchCreateReqVO request) {
        return success(batchService.create(request, getLoginUserId()));
    }

    @GetMapping("/candidate/page")
    @Operation(summary = "获得当前运营可组批的完整内容版本")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:create')")
    public CommonResult<PageResult<ContentReviewCandidateRespVO>> candidatePage(
            @Valid ContentReviewCandidatePageReqVO request) {
        return success(batchService.candidatePage(request, getLoginUserId()));
    }

    @GetMapping("/batch/page")
    @Operation(summary = "获得生产内容审核批次分页")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:query')")
    public CommonResult<PageResult<ContentReviewBatchRespVO>> page(@Valid ContentReviewBatchPageReqVO request) {
        return success(batchService.page(request, getLoginUserId()));
    }

    @GetMapping("/batch/get")
    @Operation(summary = "获得生产内容审核批次详情")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:query')")
    public CommonResult<ContentReviewBatchRespVO> get(@RequestParam Long id) {
        return success(batchService.get(id, getLoginUserId()));
    }

    @PostMapping("/batch/{batchId}/submit")
    @Operation(summary = "提交生产内容审核批次")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:submit')")
    public CommonResult<Boolean> submit(@PathVariable Long batchId,
                                        @Valid @RequestBody ContentReviewBatchSubmitReqVO request) {
        batchService.submit(batchId, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/batch/{batchId}/cancel")
    @Operation(summary = "取消尚未提交的生产内容审核批次")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:submit')")
    public CommonResult<Boolean> cancel(@PathVariable Long batchId,
                                        @Valid @RequestBody ContentReviewBatchSubmitReqVO request) {
        batchService.cancelDraft(batchId, request.getExpectedVersion(), getLoginUserId());
        return success(true);
    }

    @PutMapping("/batch/{batchId}/item/{itemId}/director-decision")
    @Operation(summary = "保存编导逐条审核结论")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:director-review')")
    public CommonResult<Boolean> saveDirectorDecision(@PathVariable Long batchId, @PathVariable Long itemId,
                                                       @Valid @RequestBody ContentReviewDecisionReqVO request) {
        batchService.saveDirectorDecision(batchId, itemId, request, getLoginUserId());
        return success(true);
    }

    @PutMapping("/batch/{batchId}/item/{itemId}/final-decision")
    @Operation(summary = "保存终审逐条审核结论")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:final-review')")
    public CommonResult<Boolean> saveFinalDecision(@PathVariable Long batchId, @PathVariable Long itemId,
                                                    @Valid @RequestBody ContentReviewDecisionReqVO request) {
        batchService.saveFinalDecision(batchId, itemId, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/batch/{batchId}/complete-director")
    @Operation(summary = "完成编导审核节点")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:director-review')")
    public CommonResult<Boolean> completeDirector(@PathVariable Long batchId,
                                                   @Valid @RequestBody ContentReviewCompleteReqVO request) {
        batchService.completeDirector(batchId, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/batch/{batchId}/complete-final")
    @Operation(summary = "完成终审节点并统一落地结论")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:final-review')")
    public CommonResult<Boolean> completeFinal(@PathVariable Long batchId,
                                                @Valid @RequestBody ContentReviewCompleteReqVO request) {
        batchService.completeFinal(batchId, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/publish")
    @Operation(summary = "登记生产内容发布结果")
    @PreAuthorize("@ss.hasPermission('zsjos:content-review:publish-register')")
    public CommonResult<Boolean> registerPublished(@PathVariable Long batchId, @PathVariable Long itemId,
                                                    @Valid @RequestBody ContentReviewPublishReqVO request) {
        batchService.registerPublished(batchId, itemId, request, getLoginUserId());
        return success(true);
    }
}
