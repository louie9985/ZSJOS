package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.*;
import cn.iocoder.yudao.module.zsjos.service.lead.SubordinateSalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 下属销售")
@RestController
@RequestMapping("/zsjos/subordinate-sales")
public class SubordinateSalesController {
    @Resource private SubordinateSalesService service;

    @GetMapping("/page")
    @Operation(summary = "获得下属销售分页")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<PageResult<SubordinateSalesRespVO>> getPage(@Valid SubordinateSalesPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }

    @PostMapping("/search-page")
    @Operation(summary = "筛选下属销售分页")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<PageResult<SubordinateSalesRespVO>> searchPage(
            @Valid @RequestBody SubordinateSalesPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/{salesUserId}/overview")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<SubordinateSalesRespVO> getOverview(@PathVariable Long salesUserId) {
        return success(service.getOverview(salesUserId, getLoginUserId()));
    }

    @GetMapping("/{salesUserId}/leads")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> getLeads(@PathVariable Long salesUserId,
                                                                    @Valid LeadManagementPageReqVO reqVO) {
        return success(service.getLeadPage(salesUserId, reqVO, getLoginUserId()));
    }

    @PostMapping("/{salesUserId}/leads/search-page")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> searchLeads(@PathVariable Long salesUserId,
            @Valid @RequestBody LeadManagementPageReqVO reqVO) {
        return success(service.getLeadPage(salesUserId, reqVO, getLoginUserId()));
    }

    @GetMapping("/{salesUserId}/tasks")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<PageResult<SubordinateTaskRespVO>> getTasks(@PathVariable Long salesUserId,
                                                                    @Valid SubordinateTaskPageReqVO reqVO) {
        return success(service.getTaskPage(salesUserId, reqVO, getLoginUserId()));
    }

    @GetMapping("/transfer-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:query')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getTransferCandidates() {
        return success(service.getTransferCandidates(getLoginUserId()));
    }

    @PutMapping("/{salesUserId}/account-status")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:account-status')")
    public CommonResult<Boolean> updateAccountStatus(@PathVariable Long salesUserId,
                                                      @Valid @RequestBody SubordinateAccountStatusReqVO reqVO) {
        service.updateAccountStatus(salesUserId, reqVO, getLoginUserId()); return success(true);
    }

    @PutMapping("/{salesUserId}/dispatch-mode")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:dispatch-mode')")
    public CommonResult<Boolean> updateDispatchMode(@PathVariable Long salesUserId,
                                                     @Valid @RequestBody SubordinateDispatchModeReqVO reqVO) {
        service.updateDispatchMode(salesUserId, reqVO, getLoginUserId()); return success(true);
    }

    @PutMapping("/dispatch-mode/pause-all")
    @Operation(summary = "暂停全部下属销售接单")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:pause-all')")
    public CommonResult<SubordinatePauseAllRespVO> pauseAllDispatch() {
        return success(service.pauseAllDispatch(getLoginUserId()));
    }

    @PostMapping("/leads/batch-transfer")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:batch-transfer')")
    public CommonResult<SubordinateBatchResultVO> batchTransfer(@Valid @RequestBody SubordinateBatchTransferReqVO reqVO) {
        return success(service.batchTransfer(reqVO, getLoginUserId()));
    }

    @PostMapping("/leads/batch-public-sea")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-sales:batch-public-sea')")
    public CommonResult<SubordinateBatchResultVO> batchPublicSea(@Valid @RequestBody SubordinateBatchPublicSeaReqVO reqVO) {
        return success(service.batchReleasePublicSea(reqVO, getLoginUserId()));
    }
}
