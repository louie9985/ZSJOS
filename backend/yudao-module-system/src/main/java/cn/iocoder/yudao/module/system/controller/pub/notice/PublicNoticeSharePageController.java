package cn.iocoder.yudao.module.system.controller.pub.notice;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.service.notice.NoticeSharePage;
import cn.iocoder.yudao.module.system.service.notice.NoticeShareService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/** Document/attachment navigation responses deliberately do not use the JSON API envelope. */
@RestController
@Slf4j
@RequestMapping("/system/notice-share")
public class PublicNoticeSharePageController {
    @Resource private NoticeShareService shareService;

    @RequestMapping(value = "/page", method = {RequestMethod.GET, RequestMethod.HEAD}, produces = "text/html;charset=UTF-8")
    @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public String page(@RequestParam(required = false) String token, HttpServletRequest request, HttpServletResponse response) {
        headers(response);
        var notice = shareService.publicNotice(token);
        return body(request, response, NoticeSharePage.notice(notice, token));
    }

    @GetMapping("/file")
    @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public void file(@RequestParam(required = false) String token, @RequestParam Long attachmentId,
                     HttpServletResponse response) {
        headers(response);
        // Revalidate sharing, tenant, publication and selected-file membership at click time.
        String location = shareService.attachmentUrl(token, attachmentId);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
    }

    private void headers(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Robots-Tag", "noindex, noarchive");
        response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; "
                + "img-src http: https:; media-src http: https:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
    }

    /** Never pass bearer query values or raw exception messages to the generic error logger. */
    @ExceptionHandler(Exception.class)
    public String failure(Exception error, HttpServletRequest request, HttpServletResponse response) {
        return body(request, response, failurePage(error, response));
    }

    private String body(HttpServletRequest request, HttpServletResponse response, String html) {
        if ("HEAD".equals(request.getMethod())) {
            // A probe must describe the GET representation, not advertise an empty document.
            response.setContentLength(html.getBytes(StandardCharsets.UTF_8).length);
            return "";
        }
        return html;
    }

    private String failurePage(Exception error, HttpServletResponse response) {
        headers(response);
        if (error instanceof ServiceException business) {
            if (NOTICE_SHARE_INVALID.getCode().equals(business.getCode())) {
                response.setStatus(HttpServletResponse.SC_GONE);
                return NoticeSharePage.error("分享内容已失效", "该链接可能已关闭，或公告已下线。请联系分享人。", false);
            }
            if (NOTICE_SHARE_RESOURCE_UNAVAILABLE.getCode().equals(business.getCode())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return NoticeSharePage.error("分享附件不可用", "该附件暂时不可用，请重试或联系分享人。", true);
            }
        }
        if (error instanceof MissingServletRequestParameterException || error instanceof MethodArgumentTypeMismatchException) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NoticeSharePage.error("分享地址不完整", "请使用分享人提供的完整链接。", false);
        }
        log.error("Public notice page request failed: {}", error.getClass().getSimpleName());
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        return NoticeSharePage.error("暂时无法加载公告", "网络或服务暂时不可用，请重试。", true);
    }
}
