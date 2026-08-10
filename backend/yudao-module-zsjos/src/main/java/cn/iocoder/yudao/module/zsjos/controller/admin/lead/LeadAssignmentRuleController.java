package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule.LeadAssignmentRuleRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule.LeadAssignmentRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 客资派单规则")
@RestController
@RequestMapping("/zsjos/lead/assignment-rule")
public class LeadAssignmentRuleController {
    @Resource private LeadDispatchService dispatchService;

    @GetMapping("/get")
    @Operation(summary = "获得派单规则")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-rule:query')")
    public CommonResult<LeadAssignmentRuleRespVO> getRule() {
        return success(dispatchService.getRule());
    }

    @PutMapping("/update")
    @Operation(summary = "更新派单规则")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-rule:update')")
    public CommonResult<Boolean> updateRule(@Valid @RequestBody LeadAssignmentRuleUpdateReqVO reqVO) {
        dispatchService.updateRule(reqVO); return success(true);
    }
}
