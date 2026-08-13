package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/lead/aging-pool")
public class LeadAgingPoolController {
    @Resource private LeadAgingPoolService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.lead.LeadTransferRequestService transferRequestService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-aging-pool:query')")
    public CommonResult<PageResult<LeadAgingPoolRespVO>> page(@Valid LeadAgingPoolPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/search-page")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-aging-pool:query')")
    public CommonResult<PageResult<LeadAgingPoolRespVO>> searchPage(@Valid @RequestBody LeadAgingPoolPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-aging-pool:query')")
    public CommonResult<LeadAgingPoolRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }
    @GetMapping("/counts")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-aging-pool:query')")
    public CommonResult<Map<String, Long>> counts() { return success(service.getCounts(getLoginUserId())); }
    @GetMapping("/filter-profile")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-aging-pool:query')")
    public CommonResult<LeadInboxFilterProfileRespVO> filterProfile() {
        return success(service.getFilterProfile(getLoginUserId()));
    }
    @GetMapping("/{id}/candidates")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-aging-pool:manage','zsjos:lead-aging-pool:manage-all')")
    public CommonResult<List<LeadAgingPoolCandidateRespVO>> candidates(@PathVariable Long id) {
        return success(service.getCandidates(id, getLoginUserId()));
    }
    @PostMapping("/{id}/assign")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-aging-pool:manage','zsjos:lead-aging-pool:manage-all')")
    public CommonResult<Boolean> assign(@PathVariable Long id, @Valid @RequestBody LeadAgingPoolAssignReqVO reqVO) {
        service.assign(id, getLoginUserId(), reqVO); return success(true);
    }
    @PostMapping("/{id}/exit")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-aging-pool:manage','zsjos:lead-aging-pool:manage-all')")
    public CommonResult<Boolean> exit(@PathVariable Long id, @Valid @RequestBody LeadAgingPoolExitReqVO reqVO) {
        service.exit(id, getLoginUserId(), reqVO); return success(true);
    }
    @PostMapping("/{id}/transfer-request")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-aging-pool:transfer-request')")
    public CommonResult<Long> requestTransfer(@PathVariable Long id,
            @Valid @RequestBody LeadTransferRequestCreateReqVO reqVO) {
        LeadAgingPoolRespVO cycle = service.get(id, getLoginUserId());
        return success(transferRequestService.create(cycle.getLeadId(), getLoginUserId(), reqVO));
    }
}
