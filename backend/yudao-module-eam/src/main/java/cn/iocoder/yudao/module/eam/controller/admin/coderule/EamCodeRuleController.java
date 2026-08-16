package cn.iocoder.yudao.module.eam.controller.admin.coderule;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.coderule.vo.EamCodeRuleRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.coderule.vo.EamCodeRuleSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.coderule.EamCodeRuleDO;
import cn.iocoder.yudao.module.eam.service.coderule.EamCodeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - EAM 资产编号规则")
@RestController
@RequestMapping("/eam/code-rule")
@Validated
public class EamCodeRuleController {

    @Resource
    private EamCodeRuleService codeRuleService;

    @PostMapping("/create")
    @Operation(summary = "创建编号规则")
    @PreAuthorize("@ss.hasPermission('eam:code-rule:create')")
    public CommonResult<Long> createCodeRule(@Valid @RequestBody EamCodeRuleSaveReqVO reqVO) {
        return success(codeRuleService.createCodeRule(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新编号规则")
    @PreAuthorize("@ss.hasPermission('eam:code-rule:update')")
    public CommonResult<Boolean> updateCodeRule(@Valid @RequestBody EamCodeRuleSaveReqVO reqVO) {
        codeRuleService.updateCodeRule(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除编号规则")
    @Parameter(name = "id", description = "规则编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:code-rule:delete')")
    public CommonResult<Boolean> deleteCodeRule(@RequestParam("id") Long id) {
        codeRuleService.deleteCodeRule(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得编号规则列表")
    @PreAuthorize("@ss.hasPermission('eam:code-rule:query')")
    public CommonResult<List<EamCodeRuleRespVO>> getCodeRuleList() {
        List<EamCodeRuleDO> list = codeRuleService.getCodeRuleList();
        return success(BeanUtils.toBean(list, EamCodeRuleRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得编号规则")
    @Parameter(name = "id", description = "规则编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:code-rule:query')")
    public CommonResult<EamCodeRuleRespVO> getCodeRule(@RequestParam("id") Long id) {
        EamCodeRuleDO rule = codeRuleService.getCodeRule(id);
        return success(BeanUtils.toBean(rule, EamCodeRuleRespVO.class));
    }

}
