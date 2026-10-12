package cn.iocoder.yudao.module.system.service.notice;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** Public HTML is a separate projection; historical/internal content is not rewritten. */
public final class NoticeShareContent {
    private NoticeShareContent() {}
    public static String clean(String html) {
        Safelist safe = Safelist.relaxed()
                .addTags("h1", "h2", "h3", "h4", "h5", "h6", "video", "source", "colgroup", "col", "span")
                .addAttributes(":all", "style")
                .addAttributes("video", "src", "poster", "controls", "preload")
                .addAttributes("source", "src", "type")
                .addProtocols("video", "src", "http", "https")
                .addProtocols("video", "poster", "http", "https")
                .addProtocols("source", "src", "http", "https")
                .addEnforcedAttribute("a", "rel", "noopener noreferrer")
                .addEnforcedAttribute("a", "target", "_blank")
                .addEnforcedAttribute("video", "controls", "controls");
        Document document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("div.notice-table-scroll").forEach(element -> element.unwrap());
        document.select("script,style,iframe,object,embed,form,input,svg,math").remove();
        // Relative/credential-bearing media would inherit the public page origin unexpectedly.
        for (var element : document.getAllElements()) {
            // The general framework cleaner admits arbitrary styles; public content only
            // keeps the explicit table-formatting subset, including text within cells.
            String style = element.closest("table") == null ? "" : NoticeShareTables.cleanStyle(element.attr("style"));
            if (style.isEmpty()) element.removeAttr("style"); else element.attr("style", style);
            for (String attribute : new String[]{"src", "href", "poster"}) {
                if (element.hasAttr(attribute) && !safeUrl(element.attr(attribute))) element.removeAttr(attribute);
            }
        }
        String cleaned = Jsoup.clean(document.body().html(), "", safe, new Document.OutputSettings().prettyPrint(false));
        Document projection = Jsoup.parseBodyFragment(cleaned);
        projection.outputSettings().prettyPrint(false);
        NoticeShareTables.prepare(projection);
        return projection.body().html();
    }
    public static boolean safeUrl(String url) {
        try {
            var uri = java.net.URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
}
