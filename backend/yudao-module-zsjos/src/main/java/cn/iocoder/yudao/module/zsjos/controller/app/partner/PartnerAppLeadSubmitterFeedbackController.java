package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterFeedbackRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmitterFeedbackService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/lead/{leadId}/submitter-feedback")
@Validated
public class PartnerAppLeadSubmitterFeedbackController {
    @Resource private LeadSubmitterFeedbackService service;
    @GetMapping("/page")
    public CommonResult<PageResult<LeadSubmitterFeedbackRespVO>> page(@PathVariable("leadId") Long leadId,
                                                                      @Valid PageParam page) {
        return success(service.pagePartner(leadId, getLoginUserId(), page));
    }
}
