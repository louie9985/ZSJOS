package cn.iocoder.yudao.module.zsjos.controller.app.partner;

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
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

import java.util.Locale;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "合作方 H5 - 需求与反馈")
@RestController
@RequestMapping("/zsjos/feedback")
@Validated
public class PartnerAppFeedbackController {

    @Resource
    private FeedbackService feedbackService;
    @Resource
    private PartnerAccountService partnerAccountService;

    @GetMapping("/portal")
    @Operation(summary = "获得反馈入口")
    public CommonResult<FeedbackRespVO.Portal> getPortal() {
        PartnerContext context = context();
        return success(feedbackService.getPartnerPortal(context.accountId(), context.partnerId()));
    }

    @GetMapping("/form")
    @Operation(summary = "获得当前反馈动态表单")
    public CommonResult<FeedbackFormRespVO> getForm(@RequestParam("type") String type) {
        context();
        return success(feedbackService.getCurrentForm(normalizeType(type)));
    }

    @PostMapping("/{type}/create")
    @Operation(summary = "提交反馈")
    public CommonResult<Long> create(@PathVariable("type") String type,
                                     @Valid @RequestBody FeedbackCreateReqVO request) {
        PartnerContext context = context();
        return success(feedbackService.createForPartner(normalizeType(type), request,
                context.accountId(), context.partnerId()));
    }

    @GetMapping("/my-page")
    @Operation(summary = "获得我的反馈分页")
    public CommonResult<PageResult<FeedbackRespVO>> getMyPage(@Valid FeedbackPageReqVO request) {
        PartnerContext context = context();
        return success(feedbackService.getPartnerPage(request, context.accountId(), context.partnerId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得反馈详情")
    public CommonResult<FeedbackRespVO> get(@PathVariable("id") Long id) {
        PartnerContext context = context();
        return success(feedbackService.getPartnerOwn(id, context.accountId(), context.partnerId()));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记反馈已读")
    public CommonResult<Boolean> markRead(@PathVariable("id") Long id,
                                          @Valid @RequestBody FeedbackActionVO.VersionedCommand request) {
        PartnerContext context = context();
        feedbackService.markReadForPartner(id, request, context.accountId(), context.partnerId());
        return success(true);
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "回复反馈")
    public CommonResult<Boolean> reply(@PathVariable("id") Long id,
                                       @Valid @RequestBody FeedbackActionVO.ReplyReq request) {
        PartnerContext context = context();
        feedbackService.replyForPartner(id, request, context.accountId(), context.partnerId());
        return success(true);
    }

    @PostMapping("/file/upload")
    @Operation(summary = "上传反馈附件")
    public CommonResult<FeedbackRespVO.FileUpload> upload(@RequestParam("file") MultipartFile file)
            throws Exception {
        PartnerContext context = context();
        FileInfoRespDTO result = feedbackService.uploadForPartner(IoUtil.readBytes(file.getInputStream()),
                file.getOriginalFilename(), file.getContentType(), context.accountId());
        return success(BeanUtils.toBean(result, FeedbackRespVO.FileUpload.class));
    }

    private PartnerContext context() {
        return partnerAccountService.requireContext(getLoginUserId());
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
