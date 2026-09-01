package cn.iocoder.yudao.module.system.controller.admin.maintenance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.admin.maintenance.vo.MaintenanceModeRespVO;
import cn.iocoder.yudao.module.system.controller.admin.maintenance.vo.MaintenanceModeUpdateReqVO;
import cn.iocoder.yudao.module.system.service.maintenance.MaintenanceModeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/system/maintenance-mode")
@TenantIgnore
public class MaintenanceModeController {
    @Resource private MaintenanceModeService service;

    @GetMapping
    @PermitAll
    @Operation(summary = "查询维护模式")
    public CommonResult<MaintenanceModeRespVO> get() {
        return success(new MaintenanceModeRespVO(service.isEnabled()));
    }

    @PutMapping
    @PreAuthorize("@ss.hasRole('super_admin')")
    @Operation(summary = "切换维护模式")
    public CommonResult<Boolean> update(@Valid @RequestBody MaintenanceModeUpdateReqVO request) {
        service.update(request.getEnabled());
        return success(true);
    }
}
