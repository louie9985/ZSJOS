package cn.iocoder.yudao.module.pms.controller.pub.kb;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.controller.admin.kb.interaction.vo.share.PmsKnowledgeDocumentSharePublicRespVO;
import cn.iocoder.yudao.module.pms.enums.kb.content.PmsKnowledgeDocumentTypeEnum;
import cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeDocumentShareService;
import cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeSharePage;
import cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeShareUrl;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

/** Document navigation deliberately returns HTML instead of the JSON API envelope. */
@RestController
@RequestMapping("/pms/kb/document-share")
public class PmsKnowledgeSharePageController {
    @Resource private PmsKnowledgeDocumentShareService shareService;
    @Resource private FileApi fileApi;
    @Resource private cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeAttachmentService attachments;

    @RequestMapping(value = "/page", method = {RequestMethod.GET, RequestMethod.HEAD}, produces = "text/html;charset=UTF-8")
    @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public String page(@RequestParam(required = false) String token, HttpServletRequest request, HttpServletResponse response) {
        headers(response);
        var document = shareService.getDocumentByShareToken(token);
        var projection = BeanUtils.toBean(document, PmsKnowledgeDocumentSharePublicRespVO.class);
        projection.setAttachments(attachments.publicList(document.getId(), token));
        return body(request, response, PmsKnowledgeSharePage.document(projection, token));
    }

    @GetMapping("/file")
    @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public void file(@RequestParam(required = false) String token, HttpServletResponse response) {
        headers(response);
        // Revalidate the same token/document lifecycle on each navigation, before signing a file URL.
        var document = shareService.getDocumentByShareToken(token);
        if (!PmsKnowledgeDocumentTypeEnum.FILE.getType().equals(document.getType())
                || document.getContent() == null || document.getContent().isBlank()) {
            throw exception(KNOWLEDGE_DOCUMENT_SHARE_FILE_UNAVAILABLE);
        }
        String location = fileApi.presignGetUrl(document.getContent(), 600);
        if (!PmsKnowledgeShareUrl.safeUrl(location)) throw exception(KNOWLEDGE_DOCUMENT_SHARE_FILE_UNAVAILABLE);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
    }

    @GetMapping("/attachment")
    @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public void attachment(@RequestParam String token, @RequestParam Long fileId, HttpServletResponse response) {
        headers(response);
        var document = shareService.getDocumentByShareToken(token);
        String location = attachments.boundUrl(document.getId(), fileId, true);
        if (!PmsKnowledgeShareUrl.safeUrl(location)) throw exception(KNOWLEDGE_DOCUMENT_SHARE_FILE_UNAVAILABLE);
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

    @ExceptionHandler(Exception.class)
    public String failure(Exception error, HttpServletRequest request, HttpServletResponse response) {
        headers(response);
        String html;
        if (error instanceof ServiceException business && KNOWLEDGE_DOCUMENT_SHARE_INVALID.getCode().equals(business.getCode())) {
            response.setStatus(HttpServletResponse.SC_GONE);
            html = PmsKnowledgeSharePage.error("分享内容已失效", "该链接可能已关闭，或文档已移入回收站。请联系分享人。", false);
        } else if (error instanceof ServiceException business && (KNOWLEDGE_DOCUMENT_SHARE_FILE_UNAVAILABLE.getCode().equals(business.getCode())
                || KNOWLEDGE_ATTACHMENT_UNAVAILABLE.getCode().equals(business.getCode()))) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            html = PmsKnowledgeSharePage.error("分享文件不可用", "文件暂时不可用，请重试或联系分享人。", true);
        } else {
            // Do not echo or log exceptions that may include bearer tokens or private file URLs.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            html = PmsKnowledgeSharePage.error("暂时无法加载文档", "网络或服务暂时不可用，请重试。", true);
        }
        return body(request, response, html);
    }

    private String body(HttpServletRequest request, HttpServletResponse response, String html) {
        if ("HEAD".equals(request.getMethod())) {
            response.setContentLength(html.getBytes(StandardCharsets.UTF_8).length);
            return "";
        }
        return html;
    }
}
