package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_SUBMIT_SPECIFY;

@Tag(name = "管理后台 - 客资销售账号")
@RestController
@RequestMapping("/zsjos/lead/sales-user")
public class LeadSalesUserController {

    @Resource
    private LeadSubmissionService leadSubmissionService;

    @GetMapping("/simple-list")
    @Operation(summary = "获得可指定的销售账号列表")
    @PreAuthorize("@ss.hasPermission('" + PERMISSION_SUBMIT_SPECIFY + "')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getSalesUserSimpleList() {
        return success(leadSubmissionService.getSpecifiedSalesUsers(getLoginUserId()));
    }

}
