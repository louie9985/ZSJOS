package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServiceAnnotationVO.*;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceAnnotationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceAnnotationConstants.*;

@Tag(name="员工工作台 - 学员课程服务批注")
@RestController
@Validated
@RequestMapping("/zsjos/student/service/{relationId}/annotation")
public class StudentServiceAnnotationController {
    @Resource private StudentServiceAnnotationService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('" + READ + "')")
    public CommonResult<Detail> detail(@PathVariable Long relationId) { return success(service.detail(relationId)); }
    @GetMapping("/options")
    @PreAuthorize("@ss.hasPermission('" + READ + "')")
    public CommonResult<List<Option>> options(@PathVariable Long relationId) { return success(service.options(relationId)); }
    @PutMapping
    @PreAuthorize("@ss.hasPermission('" + UPDATE + "')")
    public CommonResult<Detail> save(@PathVariable Long relationId, @Valid @RequestBody Save req) {
        return success(service.save(relationId, req));
    }
}
