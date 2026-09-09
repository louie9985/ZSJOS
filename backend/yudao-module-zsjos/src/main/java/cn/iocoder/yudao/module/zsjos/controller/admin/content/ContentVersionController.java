package cn.iocoder.yudao.module.zsjos.controller.admin.content;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadCompleteReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitRespVO;
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
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:content:create','zsjos:content:edit')")
    public CommonResult<Long> create(@Valid @RequestBody ContentVersionSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }

    @PostMapping("/file/upload/init")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:content:create','zsjos:content:edit')")
    @ApiAccessLog(responseEnable = true, sanitizeKeys = "uploadToken")
    public CommonResult<ZsjosDirectUploadInitRespVO> initUpload(
            @Valid @RequestBody ZsjosDirectUploadInitReqVO request) {
        return success(service.initUpload(request, getLoginUserId()));
    }

    @PostMapping("/file/upload/complete")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:content:create','zsjos:content:edit')")
    @ApiAccessLog(sanitizeKeys = "uploadToken")
    public CommonResult<ContentUploadRespVO> completeUpload(
            @Valid @RequestBody ZsjosDirectUploadCompleteReqVO request) {
        return success(service.completeUpload(request.getUploadToken(), getLoginUserId()));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("@ss.hasPermission('zsjos:content:acceptance-review')")
    public CommonResult<Boolean> review(@PathVariable Long id, @RequestParam boolean approved,
                                        @RequestParam(required = false) String comment) {
        service.review(id, approved, comment, getLoginUserId());
        return success(true);
    }
}
