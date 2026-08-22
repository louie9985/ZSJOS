package cn.iocoder.yudao.module.zsjos.controller.admin.content;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/content")
public class ContentController {
    @Resource private ContentService service;

    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:content:create')")
    public CommonResult<Long> create(@Valid @RequestBody ContentSaveReqVO req) { return success(service.create(req, getLoginUserId())); }

    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('zsjos:content:query')")
    public CommonResult<ContentRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }

    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:content:query')")
    public CommonResult<PageResult<ContentRespVO>> page(@Valid ContentPageReqVO req) { return success(service.page(req, getLoginUserId())); }

    @PostMapping("/{id}/complete-topic") @PreAuthorize("@ss.hasPermission('zsjos:content:complete-topic')")
    public CommonResult<Boolean> completeTopic(@PathVariable Long id, @RequestParam Integer version) { service.completeTopic(id, version); return success(true); }

    @PostMapping("/{id}/submit-production") @PreAuthorize("@ss.hasPermission('zsjos:content:submit-production')")
    public CommonResult<Boolean> submitProduction(@PathVariable Long id, @RequestParam Integer version) { service.submitProduction(id, version); return success(true); }

    @PostMapping("/{id}/submit-acceptance") @PreAuthorize("@ss.hasPermission('zsjos:content:submit-acceptance')")
    public CommonResult<Boolean> submitAcceptance(@PathVariable Long id, @RequestParam Integer version) { service.submitAcceptance(id, version); return success(true); }

    @PostMapping("/{id}/approve-acceptance") @PreAuthorize("@ss.hasPermission('zsjos:content:acceptance-review')")
    public CommonResult<Boolean> approveAcceptance(@PathVariable Long id, @RequestParam Integer version) { service.approveAcceptance(id, version); return success(true); }

    @PostMapping("/{id}/reject-acceptance") @PreAuthorize("@ss.hasPermission('zsjos:content:acceptance-review')")
    public CommonResult<Boolean> rejectAcceptance(@PathVariable Long id, @RequestParam Integer version,
                                                   @RequestParam String reason) {
        service.rejectAcceptance(id, version, reason);
        return success(true);
    }

    @PostMapping("/{id}/start-revision") @PreAuthorize("@ss.hasPermission('zsjos:content:revise')")
    public CommonResult<Boolean> startRevision(@PathVariable Long id, @RequestParam Integer version) { service.startRevision(id, version); return success(true); }

    @PostMapping("/{id}/resubmit-production") @PreAuthorize("@ss.hasPermission('zsjos:content:resubmit-production')")
    public CommonResult<Boolean> resubmitProduction(@PathVariable Long id, @RequestParam Integer version) { service.resubmitProduction(id, version); return success(true); }
}
