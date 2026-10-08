package cn.iocoder.yudao.module.system.controller.admin.notice;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.service.notice.NoticeShareService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/system/notice-share")
@Validated
public class NoticeShareController {
    @Resource private NoticeShareService shareService;
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:notice:query') && @ss.hasPermission('system:notice:share')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<NoticeShareRespVO> get(@RequestParam Long noticeId) { return success(shareService.get(noticeId)); }
    @PostMapping("/open")
    @PreAuthorize("@ss.hasPermission('system:notice:query') && @ss.hasPermission('system:notice:share')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<NoticeShareRespVO> open(@Valid @RequestBody NoticeShareOpenReqVO request) {
        return success(shareService.open(request, getLoginUserId()));
    }
    @PutMapping("/close")
    @PreAuthorize("@ss.hasPermission('system:notice:query') && @ss.hasPermission('system:notice:share')")
    public CommonResult<Boolean> close(@Valid @RequestBody NoticeShareCloseReqVO request) {
        shareService.close(request, getLoginUserId()); return success(true);
    }
}
