package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student-info-form/config")
public class StudentInfoConfigController {
    @Resource private StudentInfoConfigService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:config:query')")
    public CommonResult<Config> get() { return success(service.get()); }
    @PostMapping("/draft")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:config:update')")
    public CommonResult<Version> save(@Valid @RequestBody Save request) { return success(service.save(request)); }
    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:config:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody Publish request) { service.publish(request); return success(true); }
    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:config:query')")
    public CommonResult<Version> preview(@Valid @RequestBody Save request) { return success(service.preview(request)); }
}
