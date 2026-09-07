package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmitterFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/lead/{leadId}/submitter-feedback")
@Validated
public class LeadSubmitterFeedbackController {
    @Resource private LeadSubmitterFeedbackService service;

    @GetMapping("/page")
    @Operation(summary = "查看本人客资的销售反馈")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submitter-feedback:read')")
    public CommonResult<PageResult<LeadSubmitterFeedbackRespVO>> page(@PathVariable("leadId") Long leadId,
                                                                      @Valid PageParam page) {
        return success(service.page(leadId, getLoginUserId(), page));
    }
    @PostMapping
    @Operation(summary = "销售回复客资提交人")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submitter-feedback:create')")
    public CommonResult<Long> create(@PathVariable("leadId") Long leadId,
                                     @Valid @RequestBody LeadSubmitterFeedbackReqVO request) {
        return success(service.create(leadId, getLoginUserId(), request));
    }
    @PostMapping("/attachment/upload")
    @Operation(summary = "上传销售反馈临时附件")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submitter-feedback:create')")
    public CommonResult<LeadSubmitterFeedbackRespVO.Attachment> upload(@PathVariable("leadId") Long leadId,
                                        @RequestParam("file") MultipartFile file) throws IOException {
        return success(service.upload(leadId, getLoginUserId(), file));
    }
}
