package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/** Initial HTML is readable by link-preview crawlers without JavaScript or a login session. */
public final class NoticeSharePage {
    private NoticeSharePage() {}

    public static String notice(NoticeSharePublicRespVO notice, String token) {
        String content = NoticeShareContent.clean(notice.getContent());
        String summary = Jsoup.parseBodyFragment(content).text();
        if (summary.codePointCount(0, summary.length()) > 160) {
            summary = summary.substring(0, summary.offsetByCodePoints(0, 160)) + "…";
        }
        Document page = shell(notice.getTitle(), summary.isBlank() ? "中世健教育公告" : summary);
        Element article = page.selectFirst("main").appendElement("article");
        article.appendElement("h1").text(notice.getTitle());
        if (notice.getPublishTime() != null) {
            article.appendElement("p").addClass("muted").text(notice.getPublishTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
        Element body = article.appendElement("div").addClass("content").html(content);
        addImagePreviews(page, body);
        if (notice.getAttachments() != null && !notice.getAttachments().isEmpty()) {
            Element files = article.appendElement("section").addClass("files");
            files.appendElement("h2").text("附件");
            for (var file : notice.getAttachments()) {
                Element row = files.appendElement("div").addClass("file");
                row.appendElement("strong").text(file.fileName());
                row.appendElement("a").attr("href", "file?token=" + token + "&attachmentId=" + file.id())
                        .attr("target", "_blank").attr("rel", "noopener noreferrer")
                        .text("打开／下载附件");
            }
            files.appendElement("p").addClass("muted").text("图片、PDF 可由浏览器查看；其他文件请下载后打开。附件会在打开时重新验证分享状态。");
        }
        return page.outerHtml();
    }

    public static String error(String title, String message, boolean retry) {
        Document page = shell(title, message);
        Element state = page.selectFirst("main").appendElement("section").attr("role", "alert");
        state.appendElement("h1").text(title);
        state.appendElement("p").text(message);
        if (retry) state.appendElement("a").attr("href", "").text("重试");
        return page.outerHtml();
    }

    // The anonymous reader deliberately forbids scripts. Fragment links and :target
    // provide an image gallery without weakening CSP or modifying saved content.
    private static void addImagePreviews(Document page, Element body) {
        var images = body.select("img[src]").stream()
                .filter(image -> NoticeShareContent.safeUrl(image.attr("src"))).toList();
        for (int index = 0; index < images.size(); index++) {
            Element image = images.get(index);
            String id = "notice-image-" + index;
            Element link = new Element("a").attr("href", "#" + id)
                    .attr("id", id + "-source").attr("aria-label", "预览图片 " + (index + 1));
            // Preserve an authored link's other text; avoid nested anchors.
            Element authoredLink = image.closest("a");
            if (authoredLink != null) authoredLink.before(link);
            else image.replaceWith(link);
            link.appendChild(image);
            Element preview = page.body().appendElement("section").addClass("notice-image-preview")
                    .attr("id", id).attr("role", "dialog").attr("aria-label", "图片预览 " + (index + 1));
            Element toolbar = preview.appendElement("nav").attr("aria-label", "图片预览操作");
            toolbar.appendElement("a").attr("href", "#" + id + "-source").text("关闭");
            if (images.size() > 1) {
                toolbar.appendElement("a").attr("href", "#notice-image-" + (index + images.size() - 1) % images.size()).text("上一张");
                toolbar.appendElement("span").text((index + 1) + " / " + images.size());
                toolbar.appendElement("a").attr("href", "#notice-image-" + (index + 1) % images.size()).text("下一张");
            }
            toolbar.appendElement("a").attr("href", image.attr("src")).attr("target", "_blank")
                    .attr("rel", "noopener noreferrer").text("查看原图");
            preview.appendElement("img").attr("src", image.attr("src")).attr("alt", image.attr("alt"))
                    .attr("loading", "lazy");
        }
    }

    private static Document shell(String title, String summary) {
        Document page = Document.createShell("");
        page.prependChild(new DocumentType("html", "", ""));
        page.outputSettings().charset(StandardCharsets.UTF_8);
        page.selectFirst("html").attr("lang", "zh-CN");
        Element head = page.head();
        head.appendElement("meta").attr("charset", "UTF-8");
        head.appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1");
        head.appendElement("meta").attr("name", "referrer").attr("content", "no-referrer");
        head.appendElement("meta").attr("name", "robots").attr("content", "noindex, noarchive");
        head.appendElement("title").text(title);
        head.appendElement("meta").attr("property", "og:title").attr("content", title);
        head.appendElement("meta").attr("property", "og:type").attr("content", "article");
        head.appendElement("meta").attr("property", "og:site_name").attr("content", "中世健教育");
        head.appendElement("meta").attr("property", "og:description").attr("content", summary);
        head.appendElement("meta").attr("name", "description").attr("content", summary);
        head.appendElement("style").text("""
                * { box-sizing: border-box; }
                body { margin: 0; background: #f3f5f7; color: #20242b; font: 16px/1.85 system-ui, sans-serif; }
                main { width: 100%; max-width: 1120px; min-height: 100vh; margin: auto; padding: clamp(20px, 4vw, 48px) clamp(16px, 4vw, 48px) 48px; background: #fff; overflow-wrap: anywhere; }
                header { border-bottom: 1px solid #ddd; padding-bottom: 20px; margin-bottom: 28px; font-size: 18px; font-weight: 600; }
                header span { margin-left: 10px; font-size: 14px; font-weight: normal; }
                h1 { margin: 0 0 12px; font-size: 26px; line-height: 1.4; }
                h2 { font-size: 20px; }
                a { color: #1677ff; }
                .muted { color: #666; font-size: 13px; }
                .content { margin-top: 28px; }
                .content img, .content video { max-width: 100%; height: auto; }
                .content a:has(> img) { cursor: zoom-in; }
                .notice-image-preview { display: none; position: fixed; inset: 0; z-index: 1000; background: #101010; color: #fff; padding: 16px; overflow: auto; }
                .notice-image-preview:target { display: flex; flex-direction: column; align-items: center; gap: 16px; }
                body:has(.notice-image-preview:target) { overflow: hidden; }
                .notice-image-preview nav { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 8px 20px; flex-shrink: 0; }
                .notice-image-preview a { color: #fff; padding: 8px; }
                .notice-image-preview img { min-height: 0; max-width: 100%; max-height: calc(100dvh - 150px); object-fit: contain; margin: auto; }
                .content pre { display: block; max-width: 100%; overflow-x: auto; }
                .notice-table-scroll { width: 100%; max-width: 100%; overflow-x: auto; margin: 12px 0; }
                .notice-table-scroll:focus-visible { outline: 2px solid #1677ff; outline-offset: 2px; }
                .content table { width: 100%; border-collapse: collapse; }
                .content td, .content th { border: 1px solid #ddd; padding: 8px; line-height: 1.5; white-space: pre-wrap; overflow-wrap: break-word; vertical-align: top; }
                .content th { background-color: #f7f7f7; }
                .files { border-top: 1px solid #ddd; margin-top: 28px; padding-top: 16px; }
                .file { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 20px; padding: 12px 0; }
                .file strong { flex: 1 1 220px; min-width: 0; }
                .file a { display: inline-block; padding: 6px 0; }
                @media (max-width: 480px) { h1 { font-size: 23px; } }
                @media (min-width: 768px) { h1 { font-size: 32px; } }
                """);
        page.body().appendElement("main").appendElement("header").text("中世健教育")
                .appendElement("span").text("公告");
        return page;
    }
}
