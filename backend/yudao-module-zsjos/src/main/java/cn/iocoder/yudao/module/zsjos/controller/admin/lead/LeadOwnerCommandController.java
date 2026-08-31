package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadDispositionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadOwnerCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Tag(name = "管理后台 - 客资本人操作")
@RestController
@RequestMapping("/zsjos/lead/owner")
public class LeadOwnerCommandController {
    @Resource private LeadOwnerCommandService service;

    @GetMapping("/transfer-candidates")
    @Operation(summary = "获得销售本人可转派的销售列表")
    @PreAuthorize("@ss.hasPermission('" + PERMISSION_OWNER_TRANSFER + "')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getTransferCandidates() {
        return success(service.getTransferCandidates());
    }

    @PostMapping("/{leadId}/transfer")
    @Operation(summary = "销售本人转派客资")
    @PreAuthorize("@ss.hasPermission('" + PERMISSION_OWNER_TRANSFER + "')")
    public CommonResult<Boolean> transfer(@PathVariable Long leadId, @Valid @RequestBody LeadTransferReqVO reqVO) {
        service.transfer(leadId, reqVO.getSalesUserId(), getLoginUserId(), reqVO.getReason(), reqVO.getIdempotencyKey());
        return success(true);
    }

    @PostMapping("/{leadId}/release-public-sea")
    @Operation(summary = "销售本人释放客资至公海")
    @PreAuthorize("@ss.hasPermission('" + PERMISSION_OWNER_RELEASE_PUBLIC_SEA + "')")
    public CommonResult<Boolean> releasePublicSea(@PathVariable Long leadId,
                                                   @Valid @RequestBody LeadDispositionReqVO reqVO) {
        service.releaseToPublicSea(leadId, getLoginUserId(), reqVO.getReason(), reqVO.getIdempotencyKey());
        return success(true);
    }
}
