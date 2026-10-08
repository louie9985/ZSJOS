package cn.iocoder.yudao.module.system.service.notice;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** Public HTML is a separate projection; historical/internal content is not rewritten. */
public final class NoticeShareContent {
    private NoticeShareContent() {}
    public static String clean(String html) {
        Safelist safe = Safelist.relaxed()
                .addTags("h1", "h2", "h3", "h4", "h5", "h6", "video", "source")
                .addAttributes("video", "src", "poster", "controls", "preload")
                .addAttributes("source", "src", "type")
                .addProtocols("video", "src", "http", "https")
                .addProtocols("video", "poster", "http", "https")
                .addProtocols("source", "src", "http", "https")
                .addEnforcedAttribute("a", "rel", "noopener noreferrer")
                .addEnforcedAttribute("a", "target", "_blank")
                .addEnforcedAttribute("video", "controls", "controls");
        Document document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("script,style,iframe,object,embed,form,input,svg,math").remove();
        // Relative/credential-bearing media would inherit the public page origin unexpectedly.
        for (var element : document.getAllElements()) {
            for (String attribute : new String[]{"src", "href", "poster"}) {
                if (element.hasAttr(attribute) && !safeUrl(element.attr(attribute))) element.removeAttr(attribute);
            }
        }
        return Jsoup.clean(document.body().html(), "", safe, new Document.OutputSettings().prettyPrint(false));
    }
    public static boolean safeUrl(String url) {
        try {
            var uri = java.net.URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
}
