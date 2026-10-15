package cn.iocoder.yudao.module.pms.controller.admin.kb.interaction;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.controller.pub.kb.PmsKnowledgeSharePageController;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeDocumentDO;
import cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeDocumentShareService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PmsKnowledgeSharePageControllerTest {
    private static final String TOKEN = "a".repeat(32);
    private static final String ROOT = "/pms/kb/document-share";
    private PmsKnowledgeDocumentShareService service;
    private FileApi fileApi;
    private MockMvc mvc;
    private cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeAttachmentService attachments;

    @BeforeEach void setup() {
        service = mock(PmsKnowledgeDocumentShareService.class);
        fileApi = mock(FileApi.class);
        var controller = new PmsKnowledgeSharePageController();
        ReflectionTestUtils.setField(controller, "shareService", service);
        ReflectionTestUtils.setField(controller, "fileApi", fileApi);
        attachments = mock(cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeAttachmentService.class);
        ReflectionTestUtils.setField(controller, "attachments", attachments);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private PmsKnowledgeDocumentDO document() {
        return new PmsKnowledgeDocumentDO().setId(1L).setType(3).setTitle("中世健知识文档 <2026> & 说明")
                .setContent("<p>请查看课程说明。</p><table><tr><td>课程</td><td>安排</td></tr></table>");
    }

    @Test void initialHtmlAndHeadHaveTitleSummaryBodyAndSecurityHeaders() throws Exception {
        when(service.getDocumentByShareToken(TOKEN)).thenReturn(document());
        var response = mvc.perform(get(ROOT + "/page").param("token", TOKEN).header("tenant-id", "999"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();
        String html = response.getContentAsString(StandardCharsets.UTF_8);
        var page = Jsoup.parse(html);
        assertEquals(document().getTitle(), page.title());
        assertEquals(document().getTitle(), page.selectFirst("meta[property=og:title]").attr("content"));
        assertTrue(page.selectFirst("meta[name=description]").attr("content").contains("课程说明"));
        assertEquals(1, page.select("table").size());
        assertTrue(response.getHeader("Content-Security-Policy").contains("script-src 'none'"));
        mvc.perform(head(ROOT + "/page").param("token", TOKEN)).andExpect(status().isOk())
                .andExpect(content().string("")).andExpect(header().string("Content-Length",
                        String.valueOf(html.getBytes(StandardCharsets.UTF_8).length)));
        export("document.html", html);
    }

    @Test void untrustedHtmlCannotExecuteAndFileUrlsAreNotEmbedded() throws Exception {
        var document = document().setTitle("</title><script>alert(1)</script>")
                .setContent("<p onclick='bad()' style='background:url(https://evil.test)'>内容</p>"
                        + "<script>bad()</script><img src='javascript:bad()'><iframe src='https://evil.test'></iframe>");
        when(service.getDocumentByShareToken(TOKEN)).thenReturn(document);
        var response = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andExpect(status().isOk()).andReturn().getResponse();
        var page = Jsoup.parse(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(document.getTitle(), page.title());
        assertTrue(page.select("script,iframe,[onclick],[style],[src^=javascript]").isEmpty());
        document.setType(4).setTitle("课程说明.pdf").setContent("https://private.test/document.pdf");
        String html = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("private.test"));
        assertEquals("file?token=" + TOKEN, Jsoup.parse(html).selectFirst("article a").attr("href"));
        verifyNoInteractions(fileApi);
        export("file.html", html);
    }

    @Test void fileNavigationRevalidatesAndRejectsUnsafeOrUnboundFiles() throws Exception {
        var document = document().setType(4).setContent("stored-file");
        when(service.getDocumentByShareToken(TOKEN)).thenReturn(document);
        when(fileApi.presignGetUrl("stored-file", 600)).thenReturn("https://files.example.com/signed");
        mvc.perform(get(ROOT + "/file").param("token", TOKEN)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://files.example.com/signed"));
        when(fileApi.presignGetUrl("stored-file", 600)).thenReturn("javascript:bad()");
        var invalid = mvc.perform(get(ROOT + "/file").param("token", TOKEN)).andExpect(status().isNotFound()).andReturn().getResponse();
        export("file-error.html", invalid.getContentAsString(StandardCharsets.UTF_8));
        when(service.getDocumentByShareToken(TOKEN)).thenThrow(exception(KNOWLEDGE_DOCUMENT_SHARE_INVALID));
        mvc.perform(get(ROOT + "/file").param("token", TOKEN)).andExpect(status().isGone());
        verify(fileApi, times(2)).presignGetUrl("stored-file", 600);
    }

    @Test void invalidAndTransientFailuresStayDistinctWithoutLeakingToken() throws Exception {
        when(service.getDocumentByShareToken(TOKEN)).thenThrow(exception(KNOWLEDGE_DOCUMENT_SHARE_INVALID));
        String invalid = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andExpect(status().isGone())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(invalid.contains(TOKEN));
        export("invalid.html", invalid);
        mvc.perform(head(ROOT + "/page").param("token", TOKEN)).andExpect(status().isGone()).andExpect(content().string(""));
        doThrow(new IllegalStateException(TOKEN)).when(service).getDocumentByShareToken(TOKEN);
        String error = mvc.perform(get(ROOT + "/page").param("token", TOKEN)).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(error.contains(TOKEN));
        assertTrue(error.contains("重试"));
        export("error.html", error);
    }

    @Test void attachmentNavigationRevalidatesTokenAndSelection() throws Exception {
        when(service.getDocumentByShareToken(TOKEN)).thenReturn(document());
        when(attachments.boundUrl(1L, 10L, true)).thenReturn("https://files.example.test/signed");
        mvc.perform(get(ROOT + "/attachment").param("token", TOKEN).param("fileId", "10"))
                .andExpect(status().isFound()).andExpect(header().string("Location", "https://files.example.test/signed"));
        when(attachments.boundUrl(1L, 10L, true)).thenThrow(exception(KNOWLEDGE_ATTACHMENT_UNAVAILABLE));
        mvc.perform(get(ROOT + "/attachment").param("token", TOKEN).param("fileId", "10"))
                .andExpect(status().isNotFound());
        when(service.getDocumentByShareToken(TOKEN)).thenThrow(exception(KNOWLEDGE_DOCUMENT_SHARE_INVALID));
        mvc.perform(get(ROOT + "/attachment").param("token", TOKEN).param("fileId", "10"))
                .andExpect(status().isGone());
        verify(attachments, times(2)).boundUrl(1L, 10L, true);
    }

    private void export(String name, String html) throws Exception {
        Path directory = Path.of("target/pms-share-page");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name), html, StandardCharsets.UTF_8);
    }
}
