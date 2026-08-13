package cn.iocoder.yudao.module.zsjos.controller.admin.audit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.ImpersonationAuditRespVO;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/business-audit")
public class BusinessAuditController {
    @Resource private BusinessAuditService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:audit:query')")
    public CommonResult<PageResult<BusinessAuditRespVO>> page(@Valid PageParam page,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String targetType) {
        return success(service.getPage(page, actionCode, targetType));
    }

    @GetMapping("/impersonation-page")
    @PreAuthorize("@ss.hasPermission('zsjos:audit:query-impersonation')")
    public CommonResult<PageResult<ImpersonationAuditRespVO>> impersonationPage(@Valid PageParam page,
            @RequestParam(required = false) Long sessionId) {
        return success(service.getImpersonationPage(page, sessionId));
    }
}
