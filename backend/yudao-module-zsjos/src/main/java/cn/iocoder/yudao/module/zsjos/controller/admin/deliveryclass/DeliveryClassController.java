package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name="员工工作台 - 班级管理") @RestController @RequestMapping("/zsjos/delivery-class") @Validated
public class DeliveryClassController {
    @Resource private DeliveryClassService service;
    @GetMapping("/page") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-managed')")
    public CommonResult<PageResult<DeliveryClassRespVO>> page(@Valid DeliveryClassPageReqVO req){ return success(service.getManagedPage(getLoginUserId(),req)); }
    @GetMapping("/my-page") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-my')")
    public CommonResult<PageResult<DeliveryClassRespVO>> myPage(@Valid DeliveryClassPageReqVO req){ return success(service.getMyPage(getLoginUserId(),req)); }
    @GetMapping("/options")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:registration:update','zsjos:delivery-class:direct-transfer','zsjos:class-transfer:create')")
    public CommonResult<List<DeliveryClassOptionRespVO>> options(@RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "true") boolean includePending){ return success(service.options(categoryId,includePending)); }
    @GetMapping("/homeroom-candidates")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:create','zsjos:delivery-class:update')")
    public CommonResult<List<HomeroomCandidateRespVO>> candidates(){ return success(service.homeroomCandidates(getLoginUserId())); }
    @GetMapping("/category-options")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:create','zsjos:delivery-class:update')")
    public CommonResult<List<DeliveryClassCategoryOptionRespVO>> categoryOptions(){ return success(service.categoryOptions()); }
    @GetMapping("/product-options")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:create','zsjos:delivery-class:update')")
    public CommonResult<List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO>> productOptions(){ return success(service.productOptions()); }
    @GetMapping("/exam-options")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:create','zsjos:delivery-class:update')")
    public CommonResult<List<DeliveryClassExamOptionRespVO>> examOptions(@RequestParam Long categoryId,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) String selectedAttrsJson,
            @RequestParam(required = false) String selectedSkuIdsJson){ return success(service.examOptions(categoryId, productId, selectedAttrsJson, selectedSkuIdsJson)); }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-managed','zsjos:delivery-class:query-my')")
    public CommonResult<DeliveryClassRespVO> get(@PathVariable Long id){ return success(service.get(id,getLoginUserId())); }
    @GetMapping("/{id}/students") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-managed','zsjos:delivery-class:query-my')")
    public CommonResult<PageResult<DeliveryClassStudentRespVO>> students(@PathVariable Long id,@Valid PageParam pageParam){ return success(service.students(id,getLoginUserId(),pageParam)); }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:create')")
    public CommonResult<Long> create(@Valid @RequestBody DeliveryClassSaveReqVO req){ return success(service.create(req,getLoginUserId())); }
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:update')")
    public CommonResult<Boolean> update(@PathVariable Long id,@Valid @RequestBody DeliveryClassSaveReqVO req){ service.update(id,req,getLoginUserId()); return success(true); }
    @PostMapping("/{id}/complete") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:complete')")
    public CommonResult<Boolean> complete(@PathVariable Long id){ service.complete(id,getLoginUserId()); return success(true); }
    @PostMapping("/service/{relationId}/direct-transfer")
    @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:direct-transfer')")
    public CommonResult<Boolean> directTransfer(@PathVariable Long relationId,
            @Valid @RequestBody DeliveryClassDirectTransferReqVO req){ service.directTransfer(relationId,req,getLoginUserId()); return success(true); }
}
