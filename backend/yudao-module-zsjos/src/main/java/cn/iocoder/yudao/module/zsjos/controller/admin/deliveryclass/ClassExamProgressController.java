package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.ClassExamProgressVO.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.ClassExamProgressService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController @RequestMapping("/zsjos/delivery-class/{id}/exam-progress") @Validated
public class ClassExamProgressController {
    @Resource private ClassExamProgressService service;
    @GetMapping @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-managed','zsjos:delivery-class:query-my')")
    public CommonResult<View> get(@PathVariable Long id) { return success(service.get(id, getLoginUserId())); }
    @PostMapping @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:complete')")
    public CommonResult<Result> transition(@PathVariable Long id, @Valid @RequestBody Command command) {
        return success(service.transition(id, getLoginUserId(), command));
    }
    @PutMapping("/outcome") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:complete')")
    public CommonResult<View> outcome(@PathVariable Long id, @Valid @RequestBody Outcome command) {
        return success(service.outcome(id, getLoginUserId(), command));
    }
}
