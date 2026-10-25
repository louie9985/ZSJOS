package cn.iocoder.yudao.module.zsjos.controller.admin.studententry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.studententry.vo.StudentEntryVO.*;
import cn.iocoder.yudao.module.zsjos.service.studententry.StudentEntryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController @RequestMapping("/zsjos/student-entry")
@PreAuthorize("@ss.hasPermission('zsjos:student:create')")
public class StudentEntryController {
    @Resource private StudentEntryService service;
    @GetMapping("/options") public CommonResult<Options> options() { return success(service.options(getLoginUserId())); }
    @PostMapping("/check") public CommonResult<Check> check(@Valid @RequestBody Contact req) {
        return success(service.check(getLoginUserId(), req));
    }
    @PostMapping("/create") public CommonResult<Result> create(@Valid @RequestBody Create req) {
        return success(service.create(getLoginUserId(), req));
    }
}
