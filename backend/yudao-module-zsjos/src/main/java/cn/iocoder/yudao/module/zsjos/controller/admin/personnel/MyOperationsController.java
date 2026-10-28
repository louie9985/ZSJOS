package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.MyOperationsMemberRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.*;
import cn.iocoder.yudao.module.zsjos.service.personnel.MyOperationsService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/zsjos/my-operations")
@PreAuthorize("@ss.hasPermission('zsjos:my-operations:query')")
public class MyOperationsController {
    @Resource private MyOperationsService service;
    @Resource private LeadManagementService leads;
    @GetMapping("/members")
    public CommonResult<List<MyOperationsMemberRespVO>> members() { return success(service.members(getLoginUserId())); }
    @PostMapping("/{memberId}/leads/search-page")
    public CommonResult<PageResult<LeadManagementRespVO>> page(@PathVariable Long memberId, @Valid @RequestBody LeadManagementPageReqVO req) {
        return success(leads.getOperationsLeadPage(req, memberId, getLoginUserId()));
    }
    @GetMapping("/leads/filter-profile")
    public CommonResult<LeadInboxFilterProfileRespVO> profile() { return success(leads.getInboxFilterProfile(getLoginUserId(), "management")); }
    @GetMapping("/{memberId}/leads/{id}")
    public CommonResult<LeadManagementRespVO> detail(@PathVariable Long memberId, @PathVariable Long id) {
        return success(leads.getOperationsLead(memberId, id, getLoginUserId()));
    }
}
