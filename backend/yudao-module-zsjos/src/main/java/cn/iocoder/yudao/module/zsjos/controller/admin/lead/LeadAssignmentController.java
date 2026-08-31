package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客资派单关系")
@RestController
@RequestMapping("/zsjos/lead-assignment")
public class LeadAssignmentController {

    @Resource
    private LeadAssignmentService leadAssignmentService;

    @GetMapping("/relation/page")
    @Operation(summary = "获得派单关系分页")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-assignment:query')")
    public CommonResult<PageResult<LeadAssignmentRelationRespVO>> getRelationPage(
            @Valid LeadAssignmentRelationPageReqVO reqVO) {
        return success(leadAssignmentService.getRelationPage(reqVO, getLoginUserId()));
    }

    @PutMapping("/relation/save")
    @Operation(summary = "保存派单关系")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-assignment:update')")
    public CommonResult<Boolean> saveRelations(@Valid @RequestBody LeadAssignmentSaveReqVO reqVO) {
        leadAssignmentService.saveRelations(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/eligible-sales")
    @Operation(summary = "获得符合资格的销售列表")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-assignment:query')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getEligibleSalesUsers() {
        return success(leadAssignmentService.getEligibleSalesUsers());
    }

    @GetMapping("/log/page")
    @Operation(summary = "获得派单关系操作日志")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-assignment:log-query')")
    public CommonResult<PageResult<LeadAssignmentLogRespVO>> getLogPage(
            @Valid LeadAssignmentLogPageReqVO reqVO) {
        return success(leadAssignmentService.getLogPage(reqVO, getLoginUserId()));
    }

}
