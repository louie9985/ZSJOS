package cn.iocoder.yudao.module.zsjos.controller.admin.content;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.content.ContentVersionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/content/version")
public class ContentVersionController {
    @Resource private ContentVersionService service;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('zsjos:content:query')")
    public CommonResult<List<ContentVersionRespVO>> list(@RequestParam Long contentId) {
        return success(service.list(contentId, getLoginUserId()));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('zsjos:content:edit')")
    public CommonResult<Long> create(@Valid @RequestBody ContentVersionSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("@ss.hasPermission('zsjos:content:acceptance-review')")
    public CommonResult<Boolean> review(@PathVariable Long id, @RequestParam boolean approved,
                                        @RequestParam(required = false) String comment) {
        service.review(id, approved, comment, getLoginUserId());
        return success(true);
    }
}
