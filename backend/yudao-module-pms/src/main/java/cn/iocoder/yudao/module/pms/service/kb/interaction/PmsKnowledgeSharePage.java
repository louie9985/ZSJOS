package cn.iocoder.yudao.module.pms.service.kb.interaction;

import cn.iocoder.yudao.module.pms.controller.admin.kb.interaction.vo.share.PmsKnowledgeDocumentSharePublicRespVO;
import cn.iocoder.yudao.module.pms.enums.kb.content.PmsKnowledgeDocumentTypeEnum;
import cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeExcelParser;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeExcelPreviewRespVO;
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
            article.appendElement("p").text("图片、PDF 可由浏览器查看，Excel 支持表格预览；其他文件请下载后打开。");
            if (PmsKnowledgeExcelParser.supports(document.getTitle(), document.getFileType()))
                article.appendElement("p").appendElement("a").attr("href", excelLink(token, null, -1)).text("预览表格");
            article.appendElement("a").attr("href", "file?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
                    .attr("target", "_blank").attr("rel", "noopener noreferrer").text("打开／下载文件");
        } else {
            article.appendElement("div").addClass("content").html(content.isBlank() ? "<p>暂无内容</p>" : content);
        }
        if (document.getAttachments() != null && !document.getAttachments().isEmpty()) {
            var section = article.appendElement("section");
            section.appendElement("h2").text("附件");
            for (var attachment : document.getAttachments()) {
                if (PmsKnowledgeExcelParser.supports(attachment.getFileName(), attachment.getMimeType()))
                    section.appendElement("p").appendElement("a")
                            .attr("href", excelLink(token, attachment.getFileId(), -1)).text("预览表格：" + attachment.getFileName());
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

    private static String excelLink(String token, Long fileId, int sheet) {
        return "excel?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + (fileId == null ? "" : "&fileId=" + fileId) + "&sheetIndex=" + sheet;
    }

    public static String excel(PmsKnowledgeExcelPreviewRespVO preview, String token, Long fileId) {
        var page = shell("Excel 表格预览", "中世健教育知识库表格只读预览");
        var main = page.selectFirst("main");
        main.appendElement("h1").text("表格预览");
        main.appendElement("p").text("只读预览 · 公式使用文件已保存的结果 · 不含图表及打印版式");
        main.appendElement("a").attr("href", "page?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)).text("返回文档");
        var nav = main.appendElement("nav").attr("aria-label", "工作表");
        for (var sheet : preview.sheets()) {
            var link = nav.appendElement("a").attr("href", excelLink(token, fileId, sheet.index()))
                    .attr("style", "display:inline-block;margin:12px 16px 12px 0").text(sheet.name());
            if (sheet.index() == preview.sheetIndex()) link.attr("aria-current", "page");
        }
        preview.notices().forEach(notice -> main.appendElement("p").attr("role", "status").text(notice));
        if (preview.rows().isEmpty() || preview.columnCount() == 0) main.appendElement("p").text("工作表为空");
        else {
            var table = main.appendElement("div").addClass("table-scroll")
                    .attr("style", "max-height:70vh;overflow:auto").attr("tabindex", "0").appendElement("table");
            var header = table.appendElement("thead").appendElement("tr");
            header.appendElement("th").attr("scope", "col").text("行");
            for (int c = 0; c < preview.columnCount(); c++)
                header.appendElement("th").attr("scope", "col").text(org.apache.poi.ss.util.CellReference.convertNumToColString(c));
            var body = table.appendElement("tbody");
            boolean[][] covered = new boolean[preview.rows().size()][preview.columnCount()];
            var anchors = new java.util.HashMap<Integer, PmsKnowledgeExcelPreviewRespVO.Merge>();
            for (var merge : preview.merges()) {
                anchors.put(merge.row() * preview.columnCount() + merge.column(), merge);
                for (int r = merge.row(); r < merge.row() + merge.rowSpan(); r++)
                    for (int c = merge.column(); c < merge.column() + merge.colSpan(); c++)
                        if (r != merge.row() || c != merge.column()) covered[r][c] = true;
            }
            for (int r = 0; r < preview.rows().size(); r++) {
                var tr = body.appendElement("tr");
                tr.appendElement("th").attr("scope", "row").text(String.valueOf(r + 1));
                var cells = preview.rows().get(r);
                for (int c = 0; c < preview.columnCount(); c++) {
                    if (covered[r][c]) continue;
                    var cell = tr.appendElement("td").attr("style", "white-space:pre-wrap;max-width:480px")
                            .text(c < cells.size() ? cells.get(c) : "");
                    var merge = anchors.get(r * preview.columnCount() + c);
                    if (merge != null) cell.attr("rowspan", String.valueOf(merge.rowSpan()))
                            .attr("colspan", String.valueOf(merge.colSpan()));
                }
            }
        }
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
