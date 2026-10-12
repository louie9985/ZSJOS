package cn.iocoder.yudao.module.hrm.controller.admin.employeereminder;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo.HrmLifecycleReminderConfigVO;
import cn.iocoder.yudao.module.hrm.service.employee.info.HrmLifecycleReminderService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/hrm/employee-reminder/lifecycle-config")
public class HrmLifecycleReminderController {
    @Resource private HrmLifecycleReminderService service;
    @GetMapping @PreAuthorize("@ss.hasPermission('hrm:employee-reminder-config:query')")
    public CommonResult<HrmLifecycleReminderConfigVO> get() { return success(service.getConfig()); }
    @PutMapping @PreAuthorize("@ss.hasPermission('hrm:employee-reminder-config:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody HrmLifecycleReminderConfigVO r) { service.save(r); return success(true); }
    @PostMapping("/preview") @PreAuthorize("@ss.hasPermission('hrm:employee-reminder-config:query')")
    public CommonResult<java.util.Map<String, HrmLifecycleReminderService.Preview>> preview(@Valid @RequestBody HrmLifecycleReminderConfigVO r) {
        return success(service.preview(r));
    }
}
