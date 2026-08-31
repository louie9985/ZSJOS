package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客资接单与抢单")
@RestController
@RequestMapping("/zsjos/lead")
public class LeadDispatchController {
    @Resource private LeadDispatchService dispatchService;

    @GetMapping("/assignment/my-pending")
    @Operation(summary = "获得我的待接客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:accept')")
    public CommonResult<List<LeadPendingRespVO>> getMyPending() {
        return success(dispatchService.getMyPending(getLoginUserId()));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "接单")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:accept')")
    public CommonResult<Boolean> accept(@PathVariable("id") Long id) {
        dispatchService.accept(id, getLoginUserId()); return success(true);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "拒绝自动派单")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:accept')")
    public CommonResult<Boolean> reject(@PathVariable("id") Long id) {
        dispatchService.reject(id, getLoginUserId()); return success(true);
    }

    @GetMapping("/claim-pool/page")
    @Operation(summary = "获得抢单池")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:claim-pool:query')")
    public CommonResult<PageResult<LeadPendingRespVO>> getClaimPoolPage(@Valid LeadClaimPoolPageReqVO reqVO) {
        return success(dispatchService.getClaimPoolPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/claim-pool/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:claim-pool:query')")
    public CommonResult<PageResult<LeadPendingRespVO>> searchClaimPool(@Valid @RequestBody LeadClaimPoolPageReqVO reqVO) {
        return success(dispatchService.getClaimPoolPage(reqVO, getLoginUserId()));
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "抢单")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:claim')")
    public CommonResult<Boolean> claim(@PathVariable("id") Long id) {
        dispatchService.claim(id, getLoginUserId()); return success(true);
    }

    @PostMapping("/{id}/admin-transfer")
    @Operation(summary = "管理员异常转派")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:transfer')")
    public CommonResult<Boolean> adminTransfer(@PathVariable("id") Long id,
                                                @Valid @RequestBody LeadAdminTransferReqVO reqVO) {
        dispatchService.adminTransfer(id, reqVO.getSalesUserId(), getLoginUserId()); return success(true);
    }
}
