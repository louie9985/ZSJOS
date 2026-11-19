package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.EnrollmentAgreementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/enrollment-agreement")
public class EnrollmentAgreementController {
    @Resource private EnrollmentAgreementService agreements;

    @Resource private cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductService products;

    @GetMapping("/products")
    @PreAuthorize("@ss.hasPermission('zsjos:agreement:query')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductSimpleRespVO>> products() {
        return success(products.getEnabledSimpleList());
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:agreement:query')")
    public CommonResult<EnrollmentAgreementService.Template> detail(@RequestParam String productRef) {
        return success(agreements.detail(productRef));
    }

    @PutMapping("/draft")
    @PreAuthorize("@ss.hasPermission('zsjos:agreement:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody EnrollmentAgreementService.Change change) {
        agreements.save(change, false);
        return success(true);
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:agreement:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody EnrollmentAgreementService.Change change) {
        agreements.save(change, true);
        return success(true);
    }
}
