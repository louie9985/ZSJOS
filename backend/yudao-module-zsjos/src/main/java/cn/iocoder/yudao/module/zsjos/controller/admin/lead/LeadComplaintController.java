package cn.iocoder.yudao.module.zsjos.controller.admin.lead;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadComplaintService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @RequestMapping("/zsjos/lead-complaint")
public class LeadComplaintController {
    @Resource private LeadComplaintService service;
    @PostMapping("/lead/{leadId}") @PreAuthorize("@ss.hasPermission('zsjos:lead-complaint:create')")
    public CommonResult<Long> create(@PathVariable Long leadId,@Valid @RequestBody LeadComplaintCreateReqVO req){return success(service.create(leadId,getLoginUserId(),req));}
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:lead-complaint:handle')")
    public CommonResult<PageResult<LeadComplaintRespVO>> page(@Valid LeadComplaintPageReqVO req){return success(service.page(req));}
    @PostMapping("/{id}/decision") @PreAuthorize("@ss.hasPermission('zsjos:lead-complaint:handle')")
    public CommonResult<Boolean> decide(@PathVariable Long id,@Valid @RequestBody LeadComplaintDecisionReqVO req){service.decide(id,getLoginUserId(),req);return success(true);}
}
