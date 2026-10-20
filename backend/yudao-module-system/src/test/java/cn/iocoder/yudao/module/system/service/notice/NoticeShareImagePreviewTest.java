package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoticeShareImagePreviewTest {
    @Test void galleryUsesSanitizedImagesWithoutScriptsOrNestedLinks() throws Exception {
        var notice = new NoticeSharePublicRespVO();
        notice.setTitle("公告图片预览");
        notice.setContent("<p>公告图片</p><img src='https://notice-image.test/1.png' alt='第一张'>"
                + "<a href='https://notice-image.test/link'><img src='https://notice-image.test/2.png' alt='第二张'>普通链接</a>"
                + "<img src='javascript:alert(1)' onerror='bad()'><script>bad()</script>");
        String original = notice.getContent();
        String rendered = NoticeSharePage.notice(notice, "synthetic");
        var doc = Jsoup.parse(rendered);
        assertEquals(2, doc.select(".notice-image-preview").size());
        assertEquals(2, doc.select(".content a[href^=#notice-image-]").size());
        assertTrue(doc.select("script, [onerror], a a").isEmpty());
        assertEquals("普通链接", doc.selectFirst(".content a[href=https://notice-image.test/link]").text());
        assertEquals("https://notice-image.test/2.png", doc.selectFirst("#notice-image-1 img").attr("src"));
        assertEquals("#notice-image-0", doc.selectFirst("#notice-image-1 a:contains(上一张)").attr("href"));
        assertEquals("#notice-image-1-source", doc.selectFirst("#notice-image-1 a:contains(关闭)").attr("href"));
        assertEquals(original, notice.getContent());
        Path output = Path.of("target", "notice-images");
        Files.createDirectories(output);
        Files.writeString(output.resolve("server.html"), rendered, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("content.html"), NoticeShareContent.clean(original), StandardCharsets.UTF_8);
    }

    @Test void plainTextAndSingleImageNeedNoGalleryNavigation() {
        var notice = new NoticeSharePublicRespVO();
        notice.setTitle("公告");
        notice.setContent("<p>纯正文</p>");
        assertTrue(Jsoup.parse(NoticeSharePage.notice(notice, "synthetic")).select(".notice-image-preview").isEmpty());
        notice.setContent("<img src='https://notice-image.test/1.png'>");
        var doc = Jsoup.parse(NoticeSharePage.notice(notice, "synthetic"));
        assertEquals(1, doc.select(".notice-image-preview").size());
        assertTrue(doc.select("a:contains(上一张), a:contains(下一张)").isEmpty());
    }
}
