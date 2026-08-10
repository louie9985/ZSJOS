package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadFollowUpRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 客资跟进规则")
@RestController
@RequestMapping("/zsjos/lead-follow-up-rule")
public class LeadFollowUpRuleController {
    @Resource private LeadFollowUpRuleService ruleService;

    @GetMapping("/get")
    @Operation(summary = "获得客资跟进规则")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up-rule:query')")
    public CommonResult<LeadFollowUpRuleRespVO> getRule() {
        return success(ruleService.getRule());
    }

    @PutMapping("/update")
    @Operation(summary = "更新客资跟进规则")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up-rule:update')")
    public CommonResult<Boolean> updateRule(@Valid @RequestBody LeadFollowUpRuleUpdateReqVO reqVO) {
        ruleService.updateRule(reqVO);
        return success(true);
    }
}
