package cn.iocoder.yudao.module.zsjos.controller.admin.feedback;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackActionVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackFormRespVO;
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
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_BUG;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_REQUIREMENT;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_SUPPORT;

@Tag(name = "管理后台 - 员工需求与反馈")
@RestController
@RequestMapping("/zsjos/feedback")
@Validated
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    @GetMapping("/portal")
    @Operation(summary = "获得反馈工作台首页")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:query')")
    public CommonResult<FeedbackRespVO.Portal> getPortal() {
        return success(feedbackService.getPortal(getLoginUserId()));
    }

    @GetMapping("/form")
    @Operation(summary = "获得当前反馈动态表单")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:query')")
    public CommonResult<FeedbackFormRespVO> getForm(@RequestParam("type") String type) {
        return success(feedbackService.getCurrentForm(type));
    }

    @PostMapping("/requirement/create")
    @Operation(summary = "提交需求")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:requirement:create')")
    public CommonResult<Long> createRequirement(@Valid @RequestBody FeedbackCreateReqVO request) {
        return success(feedbackService.create(TYPE_REQUIREMENT, request, getLoginUserId()));
    }

    @PostMapping("/bug/create")
    @Operation(summary = "提交 BUG 反馈")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:bug:create')")
    public CommonResult<Long> createBug(@Valid @RequestBody FeedbackCreateReqVO request) {
        return success(feedbackService.create(TYPE_BUG, request, getLoginUserId()));
    }

    @PostMapping("/support/create")
    @Operation(summary = "提交技术支持")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:support:create')")
    public CommonResult<Long> createSupport(@Valid @RequestBody FeedbackCreateReqVO request) {
        return success(feedbackService.create(TYPE_SUPPORT, request, getLoginUserId()));
    }

    @PostMapping("/{id}/resubmit")
    @Operation(summary = "修改并重提被驳回需求")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:requirement:create')")
    public CommonResult<Boolean> resubmit(@PathVariable("id") Long id,
                                          @Valid @RequestBody FeedbackActionVO.ResubmitReq request) {
        feedbackService.resubmit(id, request, getLoginUserId());
        return success(true);
    }

    @GetMapping("/my-page")
    @Operation(summary = "获得我的反馈分页")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:query')")
    public CommonResult<PageResult<FeedbackRespVO>> getMyPage(@Valid FeedbackPageReqVO request) {
        return success(feedbackService.getMyPage(request, getLoginUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得本人反馈详情")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:read')")
    public CommonResult<FeedbackRespVO> get(@PathVariable("id") Long id) {
        return success(feedbackService.getOwn(id, getLoginUserId()));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记本人反馈已读")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:read')")
    public CommonResult<Boolean> markRead(@PathVariable("id") Long id,
                                          @Valid @RequestBody FeedbackActionVO.VersionedCommand request) {
        feedbackService.markRead(id, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "员工回复反馈")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:reply-self')")
    public CommonResult<Boolean> reply(@PathVariable("id") Long id,
                                       @Valid @RequestBody FeedbackActionVO.ReplyReq request) {
        feedbackService.replyOwn(id, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/survey")
    @Operation(summary = "提交满意度")
    @PreAuthorize("@ss.hasPermission('zsjos:feedback:survey:submit')")
    public CommonResult<Boolean> submitSurvey(@PathVariable("id") Long id,
                                              @Valid @RequestBody FeedbackActionVO.SurveySubmitReq request) {
        feedbackService.submitSurvey(id, request, getLoginUserId());
        return success(true);
    }

    @PostMapping("/file/upload")
    @Operation(summary = "上传反馈附件")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:feedback:requirement:create',"
            + "'zsjos:feedback:bug:create','zsjos:feedback:support:create',"
            + "'zsjos:feedback:reply-self','zsjos:feedback:reply','zsjos:feedback:complete')")
    public CommonResult<FeedbackRespVO.FileUpload> upload(@RequestParam("file") MultipartFile file)
            throws Exception {
        FileInfoRespDTO result = feedbackService.upload(IoUtil.readBytes(file.getInputStream()),
                file.getOriginalFilename(), file.getContentType(), getLoginUserId());
        return success(BeanUtils.toBean(result, FeedbackRespVO.FileUpload.class));
    }
}
