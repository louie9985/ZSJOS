package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassGroupVO.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassGroupService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/delivery-class/group")
@Validated
public class DeliveryClassGroupController {
    @Resource private DeliveryClassGroupService service;
    @GetMapping("/page") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-managed')")
    public CommonResult<Page> page(@Valid DeliveryClassPageReqVO req) { return success(service.page(getLoginUserId(),req,true)); }
    @GetMapping("/my-page") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-my')")
    public CommonResult<Page> myPage(@Valid DeliveryClassPageReqVO req) { return success(service.page(getLoginUserId(),req,false)); }
    @GetMapping("/children") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-managed')")
    public CommonResult<PageResult<DeliveryClassRespVO>> children(@RequestParam String key,@Valid DeliveryClassPageReqVO req) { return success(childPage(key,req,true)); }
    @GetMapping("/my-children") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:query','zsjos:delivery-class:query-my')")
    public CommonResult<PageResult<DeliveryClassRespVO>> myChildren(@RequestParam String key,@Valid DeliveryClassPageReqVO req) { return success(childPage(key,req,false)); }
    private PageResult<DeliveryClassRespVO> childPage(String key,DeliveryClassPageReqVO req,boolean managed) {
        return key.matches("group:[1-9][0-9]{0,17}") ? service.groupChildren(Long.parseLong(key.substring(6)),getLoginUserId(),req,managed)
                : service.children(getLoginUserId(),key,req,managed);
    }
    @GetMapping("/options") @PreAuthorize("@ss.hasAnyPermissions('zsjos:delivery-class:create','zsjos:delivery-class:update')")
    public CommonResult<PageResult<Group>> options(@Valid DeliveryClassPageReqVO req) { return success(service.options(getLoginUserId(),req)); }
    @GetMapping("/candidates") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:update') && @ss.hasPermission('zsjos:delivery-class:query-managed')")
    public CommonResult<PageResult<DeliveryClassRespVO>> candidates(@Valid DeliveryClassPageReqVO req) { return success(service.candidates(getLoginUserId(),req)); }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:create')")
    public CommonResult<Long> create(@Valid @RequestBody Save req) { return success(service.create(req,getLoginUserId())); }
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:update')")
    public CommonResult<Boolean> update(@PathVariable Long id,@Valid @RequestBody Save req) { service.update(id,req,getLoginUserId()); return success(true); }
    @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:group-delete')")
    public CommonResult<Boolean> delete(@PathVariable Long id,@RequestParam Integer version) { service.delete(id,version,getLoginUserId()); return success(true); }
    @PostMapping("/create-classes") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:create')")
    public CommonResult<Created> createClasses(@Valid @RequestBody CreateClasses req) { return success(service.createClasses(req,getLoginUserId())); }
    @PostMapping("/move") @PreAuthorize("@ss.hasPermission('zsjos:delivery-class:update')")
    public CommonResult<Boolean> move(@Valid @RequestBody Move req) { service.move(req,getLoginUserId()); return success(true); }
}
