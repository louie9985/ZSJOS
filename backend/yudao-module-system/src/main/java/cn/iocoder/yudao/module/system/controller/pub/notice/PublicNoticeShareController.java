package cn.iocoder.yudao.module.system.controller.pub.notice;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
import cn.iocoder.yudao.module.system.service.notice.NoticeShareService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@Slf4j
@RequestMapping("/system/notice-share")
public class PublicNoticeShareController {
    @Resource private NoticeShareService shareService;
    @GetMapping("/get") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<NoticeSharePublicRespVO> get(
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Notice-Share-Token", required = false) String headerToken,
            HttpServletResponse response) {
        noCache(response);
        return success(shareService.publicNotice(headerToken != null ? headerToken : token));
    }
    @GetMapping("/attachment-url") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<String> attachmentUrl(
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Notice-Share-Token", required = false) String headerToken,
            @RequestParam Long attachmentId, HttpServletResponse response) {
        noCache(response);
        return success(shareService.attachmentUrl(headerToken != null ? headerToken : token, attachmentId));
    }
    private void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
    /** The generic error logger captures query parameters; bearer links must never reach it. */
    @ExceptionHandler(Exception.class)
    public CommonResult<?> failure(Exception error, HttpServletResponse response) {
        noCache(response);
        if (error instanceof ServiceException business) return CommonResult.error(business.getCode(), business.getMessage());
        log.error("Public notice sharing request failed: {}", error.getClass().getSimpleName());
        return CommonResult.error(500, "服务暂时不可用，请重试");
    }
}
