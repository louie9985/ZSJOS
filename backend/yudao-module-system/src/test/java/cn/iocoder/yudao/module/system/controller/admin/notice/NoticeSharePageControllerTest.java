package cn.iocoder.yudao.module.system.controller.admin.notice;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.pub.notice.PublicNoticeSharePageController;
import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
import cn.iocoder.yudao.module.system.service.notice.NoticeShareService;
import jakarta.annotation.security.PermitAll;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NoticeSharePageControllerTest {
    private static final String TOKEN = "a".repeat(43);
    private static final String ROOT = "/system/notice-share";
    private NoticeShareService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(NoticeShareService.class);
        var controller = new PublicNoticeSharePageController();
        ReflectionTestUtils.setField(controller, "shareService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private NoticeSharePublicRespVO notice() {
        var notice = new NoticeSharePublicRespVO();
        notice.setTitle("中世健考试安排 <2026> & \"说明\"");
        notice.setContent("<p>请及时查看考试安排。</p><table><tr><td>课程说明</td><td>十月安排</td></tr></table>");
        notice.setPublishTime(LocalDateTime.of(2026, 10, 9, 16, 0));
        notice.setAttachments(List.of(new NoticeSharePublicRespVO.Attachment(101L, "课程说明.pdf", "application/pdf", 1024L)));
        return notice;
    }

    @Test void initialHttpHtmlContainsTitleBodyMetadataAndSelectedFilesWithoutJavascript() throws Exception {
        when(service.publicNotice(TOKEN)).thenReturn(notice());
        var response = mvc.perform(get(ROOT + "/page").param("token", TOKEN)
                        .header("User-Agent", "WeCom preview").header("tenant-id", "999"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        assertEquals("UTF-8", response.getCharacterEncoding());
        String html = response.getContentAsString(StandardCharsets.UTF_8);
        var page = Jsoup.parse(html);
        assertEquals(notice().getTitle(), page.title());
        assertEquals(notice().getTitle(), page.selectFirst("meta[property=og:title]").attr("content"));
        assertEquals(notice().getTitle(), page.selectFirst("h1").text());
        assertTrue(page.selectFirst("meta[name=description]").attr("content").contains("考试安排"));
        assertEquals(1, page.select("table").size());
        assertEquals(1, page.select(".files a").size());
        assertEquals("file?token=" + TOKEN + "&attachmentId=101", page.selectFirst(".files a").attr("href"));
        assertTrue(page.select("script, iframe, meta[http-equiv=refresh]").isEmpty());
        assertTrue(response.getHeader("Content-Security-Policy").contains("script-src 'none'"));
        verify(service).publicNotice(TOKEN);
        export("notice.html", html);
        mvc.perform(head(ROOT + "/page").param("token", TOKEN))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Content-Length", String.valueOf(html.getBytes(StandardCharsets.UTF_8).length)))
                .andExpect(content().string(""));
    }

    @Test void maliciousTitlesSummariesAndFilenamesCannotEscapeHtmlAttributes() throws Exception {
        var notice = notice();
        notice.setTitle("</title><script>alert(1)</script>\" & <img src=x onerror=bad()>");
        notice.setContent("<script>bad()</script><p onclick='bad()'>摘要\" & 内容</p><img src='javascript:bad()'>");
        notice.setAttachments(List.of(new NoticeSharePublicRespVO.Attachment(101L, "<img onerror=bad()>.pdf", null, 1L)));
        when(service.publicNotice(TOKEN)).thenReturn(notice);
        var response = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andExpect(status().isOk()).andReturn().getResponse();
        var html = Jsoup.parse(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(notice.getTitle(), html.title());
        assertTrue(html.select("script, [onclick], [onerror], [src^=javascript]").isEmpty());
        assertEquals("摘要\" & 内容", html.selectFirst("meta[property=og:description]").attr("content"));
        assertEquals("<img onerror=bad()>.pdf", html.selectFirst(".file strong").text());
    }

    @Test void invalidShareAndTransientErrorsReturnSafeHtmlWithoutLeakingInput() throws Exception {
        when(service.publicNotice(TOKEN)).thenThrow(exception(NOTICE_SHARE_INVALID));
        String invalid = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andExpect(status().isGone())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("分享内容已失效", Jsoup.parse(invalid).title());
        assertFalse(invalid.contains(TOKEN));
        export("invalid.html", invalid);
        mvc.perform(head(ROOT + "/page").param("token", TOKEN)).andExpect(status().isGone())
                .andExpect(content().string(""));
        doThrow(new IllegalStateException("private bearer detail")).when(service).publicNotice(TOKEN);
        String failed = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(failed.contains("private bearer detail"));
        assertEquals("重试", Jsoup.parse(failed).selectFirst("a").text());
        export("error.html", failed);
    }

    @Test void fileNavigationRevalidatesAndDoesNotExposeUrlsOnDeniedOrMissingFiles() throws Exception {
        when(service.attachmentUrl(TOKEN, 101L)).thenReturn("https://files.example.com/selected.pdf");
        mvc.perform(get(ROOT + "/file").param("token", TOKEN).param("attachmentId", "101"))
                .andExpect(status().isFound()).andExpect(header().string("Location", "https://files.example.com/selected.pdf"))
                .andExpect(header().string("Cache-Control", "no-store"));
        when(service.attachmentUrl(TOKEN, 101L)).thenThrow(exception(NOTICE_SHARE_INVALID));
        mvc.perform(get(ROOT + "/file").param("token", TOKEN).param("attachmentId", "101"))
                .andExpect(status().isGone()).andExpect(header().doesNotExist("Location"));
        when(service.attachmentUrl(TOKEN, 102L)).thenThrow(exception(NOTICE_SHARE_RESOURCE_UNAVAILABLE));
        String missing = mvc.perform(get(ROOT + "/file").param("token", TOKEN).param("attachmentId", "102"))
                .andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("分享附件不可用", Jsoup.parse(missing).title());
        export("file-error.html", missing);
        mvc.perform(get(ROOT + "/file").param("token", TOKEN).param("attachmentId", "bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/file").param("token", TOKEN)).andExpect(status().isBadRequest());
        verify(service, times(2)).attachmentUrl(TOKEN, 101L);
    }

    @Test void anonymousNavigationDisablesSensitiveRequestAndResponseLogging() {
        for (var method : PublicNoticeSharePageController.class.getDeclaredMethods()) {
            if (!List.of("page", "file").contains(method.getName())) continue;
            assertNotNull(method.getAnnotation(PermitAll.class));
            assertNotNull(method.getAnnotation(TenantIgnore.class));
            var logging = method.getAnnotation(ApiAccessLog.class);
            assertFalse(logging.requestEnable());
            assertFalse(logging.responseEnable());
        }
    }

    private static void export(String name, String html) throws Exception {
        Path output = Path.of("target", "notice-share-page");
        Files.createDirectories(output);
        Files.writeString(output.resolve(name), html, StandardCharsets.UTF_8);
    }
}
