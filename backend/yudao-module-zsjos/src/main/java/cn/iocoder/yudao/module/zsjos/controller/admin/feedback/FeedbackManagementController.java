package cn.iocoder.yudao.module.zsjos.controller.admin.feedback;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackActionVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackRespVO;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackService;
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
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_BUG;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_REQUIREMENT;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_SUPPORT;

@Tag(name = "管理后台 - 反馈管理")
@RestController
@RequestMapping("/zsjos/feedback-management")
@Validated
public class FeedbackManagementController {

    @Resource
    private FeedbackService feedbackService;

    @GetMapping("/requirement/page")
    @Operation(summary = "获得需求反馈分页")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:requirement:manage')")
    public CommonResult<PageResult<FeedbackRespVO>> getRequirementPage(
            @Valid FeedbackPageReqVO request) {
        return success(feedbackService.getAdminPage(TYPE_REQUIREMENT, request, getLoginUserId()));
    }

    @GetMapping("/bug/page")
    @Operation(summary = "获得 BUG 反馈分页")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:bug:manage')")
    public CommonResult<PageResult<FeedbackRespVO>> getBugPage(@Valid FeedbackPageReqVO request) {
        return success(feedbackService.getAdminPage(TYPE_BUG, request, getLoginUserId()));
    }

    @GetMapping("/support/page")
    @Operation(summary = "获得技术支持分页")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:support:manage')")
    public CommonResult<PageResult<FeedbackRespVO>> getSupportPage(@Valid FeedbackPageReqVO request) {
        return success(feedbackService.getAdminPage(TYPE_SUPPORT, request, getLoginUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得反馈管理详情")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:query-admin')")
    public CommonResult<FeedbackRespVO> get(@PathVariable("id") Long id) {
        return success(feedbackService.getAdmin(id, getLoginUserId()));
    }

    @PutMapping("/{id}/assign")
    @Operation(summary = "分派或改派反馈")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:assign')")
    public CommonResult<Boolean> assign(@PathVariable("id") Long id,
                                        @Valid @RequestBody FeedbackActionVO.AssignReq request) {
        feedbackService.assign(id, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "后台回复反馈")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:reply')")
    public CommonResult<Boolean> reply(@PathVariable("id") Long id,
                                       @Valid @RequestBody FeedbackActionVO.ReplyReq request) {
        feedbackService.replyAdmin(id, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "完成反馈")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:complete')")
    public CommonResult<Boolean> complete(@PathVariable("id") Long id,
                                          @Valid @RequestBody FeedbackActionVO.CompleteReq request) {
        feedbackService.complete(id, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/survey")
    @Operation(summary = "发起满意度调研")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:survey')")
    public CommonResult<Boolean> requestSurvey(
            @PathVariable("id") Long id,
            @Valid @RequestBody FeedbackActionVO.VersionedCommand request) {
        feedbackService.requestSurvey(id, request, getLoginUserId());
        return success(true);
    }

    @GetMapping("/settings/list")
    @Operation(summary = "获得反馈设置")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:settings')")
    public CommonResult<List<FeedbackConfigVO.Resp>> getConfigs() {
        return success(feedbackService.getConfigs());
    }

    @PutMapping("/settings")
    @Operation(summary = "保存反馈设置")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:settings:save')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody FeedbackConfigVO.SaveReq request) {
        feedbackService.saveConfig(request, getLoginUserId());
        return success(true);
    }

    @GetMapping("/settings/candidates")
    @Operation(summary = "获得分派负责人和处理人候选")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:feedback:settings',"
            + "'zsjos:feedback:assign')")
    public CommonResult<List<FeedbackConfigVO.UserOption>> getCandidates(
            @RequestParam("type") String type) {
        return success(feedbackService.getCandidates(type));
    }

    @GetMapping("/settings/form-options")
    @Operation(summary = "获得可绑定动态表单")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:settings')")
    public CommonResult<List<FeedbackConfigVO.FormOption>> getFormOptions() {
        return success(feedbackService.getFormOptions());
    }

    @GetMapping("/settings/process-options")
    @Operation(summary = "获得可绑定已发布流程")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:settings')")
    public CommonResult<List<FeedbackConfigVO.ProcessOption>> getProcessOptions() {
        return success(feedbackService.getProcessOptions());
    }
}
