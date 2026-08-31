package cn.iocoder.yudao.module.zsjos.controller.admin.impersonation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.impersonation.vo.ImpersonationSessionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.impersonation.vo.ImpersonationStartReqVO;
import cn.iocoder.yudao.module.zsjos.service.impersonation.ImpersonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 借视图")
@RestController
@RequestMapping("/zsjos/impersonation")
public class ImpersonationController {
    @Resource private ImpersonationService service;

    @PostMapping("/start")
    @PreAuthorize("@ss.hasPermission('zsjos:impersonation:start')")
    @Operation(summary = "开始只读借视图会话")
    public CommonResult<ImpersonationSessionRespVO> start(@Valid @RequestBody ImpersonationStartReqVO request) {
        if (SecurityFrameworkUtils.skipPermissionCheck()) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.IMPERSONATION_READ_ONLY);
        }
        return success(service.start(WebFrameworkUtils.getLoginUserId(), request.getTargetUserId(), request.getReason()));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("@ss.hasPermission('zsjos:impersonation:start')")
    @Operation(summary = "结束借视图会话")
    public CommonResult<Boolean> end(@PathVariable Long id,
                                     @RequestParam(defaultValue = "manual") @Size(max = 500) String reason) {
        service.end(WebFrameworkUtils.getLoginUserId(), id, reason);
        return success(true);
    }
}
