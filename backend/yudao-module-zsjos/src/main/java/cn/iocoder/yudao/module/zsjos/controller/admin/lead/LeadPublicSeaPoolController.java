package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.publicseapool.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadPublicSeaPoolService;
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
@RequestMapping("/zsjos/lead/public-sea-pool")
public class LeadPublicSeaPoolController {
    @Resource private LeadPublicSeaPoolService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.lead.LeadTransferRequestService transferRequestService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-public-sea-pool:query')")
    public CommonResult<PageResult<LeadPublicSeaPoolRespVO>> page(@Valid LeadPublicSeaPoolPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead-public-sea-pool:query')")
    public CommonResult<PageResult<LeadPublicSeaPoolRespVO>> searchPage(@Valid @RequestBody LeadPublicSeaPoolPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-public-sea-pool:query')")
    public CommonResult<LeadPublicSeaPoolRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }
    @GetMapping("/counts")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-public-sea-pool:query')")
    public CommonResult<Map<String, Long>> counts() { return success(service.getCounts(getLoginUserId())); }
    @GetMapping("/filter-profile")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-public-sea-pool:query')")
    public CommonResult<LeadInboxFilterProfileRespVO> filterProfile() {
        return success(service.getFilterProfile(getLoginUserId()));
    }
    @GetMapping("/{id}/candidates")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-public-sea-pool:manage','zsjos:lead-public-sea-pool:manage-all')")
    public CommonResult<List<LeadPublicSeaPoolCandidateRespVO>> candidates(@PathVariable Long id) {
        return success(service.getCandidates(id, getLoginUserId()));
    }
    @PostMapping("/{id}/assign")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-public-sea-pool:manage','zsjos:lead-public-sea-pool:manage-all')")
    public CommonResult<Boolean> assign(@PathVariable Long id, @Valid @RequestBody LeadPublicSeaPoolAssignReqVO reqVO) {
        service.assign(id, getLoginUserId(), reqVO); return success(true);
    }
    @PostMapping("/{id}/exit")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-public-sea-pool:manage','zsjos:lead-public-sea-pool:manage-all')")
    public CommonResult<Boolean> exit(@PathVariable Long id, @Valid @RequestBody LeadPublicSeaPoolExitReqVO reqVO) {
        service.exit(id, getLoginUserId(), reqVO); return success(true);
    }
    @PostMapping("/{id}/transfer-request")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-public-sea-pool:transfer-request')")
    public CommonResult<Long> requestTransfer(@PathVariable Long id,
            @Valid @RequestBody LeadTransferRequestCreateReqVO reqVO) {
        return success(transferRequestService.create(id, getLoginUserId(), reqVO));
    }
}
