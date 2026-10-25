package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/student/service/{relationId}")
public class TeachingServiceLifecycleController {
    @Resource private TeachingServiceLifecycleService service;
    @GetMapping("/lifecycle") @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<TeachingServiceLifecycleRespVO> detail(@PathVariable Long relationId) {
        return success(service.detail(relationId,getLoginUserId()));
    }
    @GetMapping("/restore-targets") @PreAuthorize("@ss.hasPermission('zsjos:student-service:restore')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassOptionRespVO>> targets(@PathVariable Long relationId) {
        return success(service.restoreTargets(relationId,getLoginUserId()));
    }
    @PostMapping("/close") @PreAuthorize("@ss.hasPermission('zsjos:student-service:close')")
    public CommonResult<Boolean> close(@PathVariable Long relationId,@Valid @RequestBody TeachingServiceCommandReqVO req) {
        service.close(relationId,getLoginUserId(),req); return success(true);
    }
    @PostMapping("/restore") @PreAuthorize("@ss.hasPermission('zsjos:student-service:restore')")
    public CommonResult<Boolean> restore(@PathVariable Long relationId,@Valid @RequestBody TeachingServiceCommandReqVO req) {
        service.restore(relationId,getLoginUserId(),req); return success(true);
    }
}
