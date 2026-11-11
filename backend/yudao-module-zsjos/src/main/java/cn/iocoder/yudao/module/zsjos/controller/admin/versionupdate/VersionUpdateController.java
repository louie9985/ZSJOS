package cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate.vo.VersionUpdateVO.*;
import cn.iocoder.yudao.module.zsjos.service.versionupdate.VersionUpdateService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/zsjos/version-update")
public class VersionUpdateController {
    @Resource private VersionUpdateService service;
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:version-update:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<PageResult<Item>> page(@Valid Query query) { return success(service.page(query)); }
    @GetMapping("/recipient-options") @PreAuthorize("@ss.hasPermission('zsjos:version-update:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Options> options() { return success(service.options()); }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:version-update:create')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Long> create(@Valid @RequestBody Save request) { return success(service.create(request)); }
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:version-update:update')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody Save request) { service.update(id, request); return success(true); }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:version-update:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Item> detail(@PathVariable Long id) { return success(service.detail(id)); }
    @PostMapping("/{id}/publish") @PreAuthorize("@ss.hasPermission('zsjos:version-update:publish')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Boolean> publish(@PathVariable Long id) { service.publish(id); return success(true); }
    @PostMapping("/{id}/offline") @PreAuthorize("@ss.hasPermission('zsjos:version-update:offline')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Boolean> offline(@PathVariable Long id) { service.offline(id); return success(true); }
    @GetMapping("/{id}/recipients") @PreAuthorize("@ss.hasPermission('zsjos:version-update:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<PageResult<Recipient>> recipients(@PathVariable Long id, @Valid Query query) { return success(service.recipients(id, query)); }
    @GetMapping("/my-page") @PreAuthorize("@ss.hasPermission('zsjos:version-update:read')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<PageResult<Item>> mine(@Valid Query query) { return success(service.mine(query)); }
    @GetMapping("/my/{id}") @PreAuthorize("@ss.hasPermission('zsjos:version-update:read')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Item> mine(@PathVariable Long id) { return success(service.myDetail(id)); }
    @PostMapping("/my/{id}/acknowledge") @PreAuthorize("@ss.hasPermission('zsjos:version-update:acknowledge')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Boolean> acknowledge(@PathVariable Long id) { service.acknowledge(id); return success(true); }
    @PostMapping("/attachment") @PreAuthorize("@ss.hasAnyPermissions('zsjos:version-update:create','zsjos:version-update:update')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Attachment> upload(@RequestParam MultipartFile file) throws Exception { return success(service.upload(file)); }
}
