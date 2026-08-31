package cn.iocoder.yudao.module.eam.controller.admin.publicedit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.eam.service.publicedit.EamPublicEditService;
import cn.iocoder.yudao.module.eam.service.publicedit.EamPublicEditService.CodeResult;
import cn.iocoder.yudao.module.eam.controller.admin.publicedit.vo.PublicEditCodeUpdateReqVO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - EAM 公开编辑口令")
@RestController
@RequestMapping("/eam/public-edit-code")
public class PublicEditCodeController {
    @Resource private EamPublicEditService service;
    @GetMapping("/me") @Operation(summary = "查看我的口令")
    @PreAuthorize("@ss.hasPermission('eam:asset:public-edit-code')")
    public CommonResult<CodeResult> me() { return success(service.getOrCreateForCurrentUser(SecurityFrameworkUtils.getLoginUserId(), false)); }
    @PostMapping("/generate") @Operation(summary = "生成我的口令")
    @PreAuthorize("@ss.hasPermission('eam:asset:public-edit-code')")
    public CommonResult<CodeResult> generate() { return success(service.getOrCreateForCurrentUser(SecurityFrameworkUtils.getLoginUserId(), true)); }
    @PutMapping("/me") @Operation(summary = "修改我的口令")
    @PreAuthorize("@ss.hasPermission('eam:asset:public-edit-code')")
    public CommonResult<CodeResult> updateMe(@Valid @RequestBody PublicEditCodeUpdateReqVO reqVO) {
        return success(service.updateForCurrentUser(SecurityFrameworkUtils.getLoginUserId(), reqVO.getCode()));
    }
    @PutMapping("/reset/{userId}") @Operation(summary = "重置员工口令")
    @PreAuthorize("@ss.hasPermission('eam:asset:public-edit-code')")
    public CommonResult<CodeResult> reset(@PathVariable Long userId) { return success(service.resetForUser(userId)); }
}
