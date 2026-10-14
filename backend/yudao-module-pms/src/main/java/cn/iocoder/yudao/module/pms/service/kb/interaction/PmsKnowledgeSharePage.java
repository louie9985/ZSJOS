package cn.iocoder.yudao.module.pms.service.kb.interaction;

import cn.iocoder.yudao.module.pms.controller.admin.kb.interaction.vo.share.PmsKnowledgeDocumentSharePublicRespVO;
import cn.iocoder.yudao.module.pms.enums.kb.content.PmsKnowledgeDocumentTypeEnum;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.safety.Safelist;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** A public document projection; persisted/internal editor content is never rewritten. */
public final class PmsKnowledgeSharePage {
    private PmsKnowledgeSharePage() {}

    public static String document(PmsKnowledgeDocumentSharePublicRespVO document, String token) {
        boolean file = PmsKnowledgeDocumentTypeEnum.FILE.getType().equals(document.getType());
        String content = file ? "" : clean(document.getContent());
        String summary = Jsoup.parseBodyFragment(content).text();
        if (summary.codePointCount(0, summary.length()) > 160) {
            summary = summary.substring(0, summary.offsetByCodePoints(0, 160)) + "…";
        }
        Document page = shell(document.getTitle(), summary.isBlank() ? "中世健教育知识库文档" : summary);
        var article = page.selectFirst("main").appendElement("article");
        article.appendElement("h1").text(document.getTitle());
        if (file) {
            article.appendElement("p").text("图片、PDF 可由浏览器查看；其他文件请下载后打开。");
            article.appendElement("a").attr("href", "file?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
                    .attr("target", "_blank").attr("rel", "noopener noreferrer").text("打开／下载文件");
        } else {
            article.appendElement("div").addClass("content").html(content.isBlank() ? "<p>暂无内容</p>" : content);
        }
        if (document.getAttachments() != null && !document.getAttachments().isEmpty()) {
            var section = article.appendElement("section");
            section.appendElement("h2").text("附件");
            for (var attachment : document.getAttachments()) {
                section.appendElement("p").appendElement("a")
                        .attr("href", "attachment?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) + "&fileId=" + attachment.getFileId())
                        .attr("target", "_blank").attr("rel", "noopener noreferrer").text(attachment.getFileName());
            }
        }
        return page.outerHtml();
    }

    public static String error(String title, String message, boolean retry) {
        var page = shell(title, message);
        var state = page.selectFirst("main").appendElement("section").attr("role", "alert");
        state.appendElement("h1").text(title);
        state.appendElement("p").text(message);
        if (retry) state.appendElement("a").attr("href", "").text("重试");
        return page.outerHtml();
    }

    private static String clean(String html) {
        var document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("script,style,iframe,object,embed,form,input,svg,math").remove();
        // Only absolute HTTP resources are meaningful on the separate public origin.
        for (var element : document.getAllElements()) {
            for (String attribute : new String[]{"src", "href", "poster"}) {
                if (element.hasAttr(attribute) && !PmsKnowledgeShareUrl.safeUrl(element.attr(attribute))) {
                    element.removeAttr(attribute);
                }
            }
        }
        var safe = Safelist.relaxed().addTags("h1", "h2", "h3", "h4", "h5", "h6", "video", "source")
                .addAttributes("video", "src", "poster", "controls")
                .addAttributes("source", "src", "type")
                .addProtocols("video", "src", "http", "https").addProtocols("video", "poster", "http", "https")
                .addProtocols("source", "src", "http", "https")
                .addEnforcedAttribute("video", "controls", "controls")
                .addEnforcedAttribute("a", "target", "_blank").addEnforcedAttribute("a", "rel", "noopener noreferrer");
        var projection = Jsoup.parseBodyFragment(Jsoup.clean(document.body().html(), safe));
        projection.select("table").forEach(table -> table.wrap("<div class=table-scroll></div>"));
        return projection.body().html();
    }

    private static Document shell(String title, String summary) {
        var page = Document.createShell("");
        page.prependChild(new DocumentType("html", "", ""));
        page.outputSettings().charset(StandardCharsets.UTF_8);
        page.selectFirst("html").attr("lang", "zh-CN");
        var head = page.head();
        head.appendElement("meta").attr("charset", "UTF-8");
        head.appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1");
        head.appendElement("meta").attr("name", "referrer").attr("content", "no-referrer");
        head.appendElement("title").text(title);
        head.appendElement("meta").attr("property", "og:title").attr("content", title);
        head.appendElement("meta").attr("property", "og:type").attr("content", "article");
        head.appendElement("meta").attr("property", "og:site_name").attr("content", "中世健教育");
        head.appendElement("meta").attr("property", "og:description").attr("content", summary);
        head.appendElement("meta").attr("name", "description").attr("content", summary);
        head.appendElement("style").text("""
                * { box-sizing: border-box; }
                body { margin: 0; color: #20242b; background: white; font: 16px/1.85 system-ui, sans-serif; }
                main { max-width: 860px; margin: auto; padding: 24px 20px 48px; overflow-wrap: anywhere; }
                header { border-bottom: 1px solid #ddd; padding-bottom: 18px; margin-bottom: 24px; }
                h1 { font-size: 26px; line-height: 1.4; }
                a { color: #1677ff; }
                .content img, .content video { max-width: 100%; height: auto; }
                pre, .table-scroll { max-width: 100%; overflow-x: auto; }
                table { width: 100%; border-collapse: collapse; }
                td, th { border: 1px solid #ddd; padding: 8px; min-width: 90px; }
                """);
        page.body().appendElement("main").appendElement("header").text("中世健教育 · 知识库");
        return page;
    }
}
