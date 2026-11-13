package cn.iocoder.yudao.module.zsjos.service.examcollection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/** Stored document formatting is restricted before anonymous rendering. */
public final class ExamCollectionContent {
    private ExamCollectionContent() {}
    public static String clean(String input) {
        if (input == null || input.isBlank()) return "";
        var document = Jsoup.parseBodyFragment(input);
        document.select("script,style,iframe,object,embed,svg,math,form").remove();
        for (var element : document.select("[style]")) {
            var styles = new ArrayList<String>();
            for (String declaration : element.attr("style").split(";")) {
                String[] pair = declaration.split(":", 2);
                if (pair.length != 2) continue;
                String key = pair[0].trim().toLowerCase(Locale.ROOT);
                String value = pair[1].trim().toLowerCase(Locale.ROOT);
                if (allowed(key, value)) styles.add(key + ":" + value);
            }
            element.attr("style", String.join(";", styles));
        }
        var safe = Safelist.relaxed().addTags("span", "s", "colgroup", "col", "h1", "h2", "h3", "h4", "h5", "h6")
                .addTags("video", "source").addAttributes("video", "src", "poster", "controls", "preload")
                .addAttributes("source", "src", "type").addProtocols("video", "src", "http", "https")
                .addProtocols("video", "poster", "http", "https").addProtocols("source", "src", "http", "https")
                .addAttributes(":all", "style").addAttributes("col", "width", "span")
                .addAttributes("img", "width", "height").addAttributes("table", "width")
                .addEnforcedAttribute("a", "target", "_blank").addEnforcedAttribute("a", "rel", "noopener noreferrer");
        return Jsoup.clean(document.body().html(), "", safe, new Document.OutputSettings().prettyPrint(false));
    }
    private static boolean allowed(String key, String value) {
        if (Set.of("color", "background-color").contains(key)) {
            return value.matches("#[0-9a-f]{3,8}|[a-z]{1,20}|rgba?\\([0-9.,% ]{1,40}\\)");
        }
        if (Set.of("width", "height", "min-width", "max-width", "font-size", "line-height").contains(key)) {
            return value.matches("[0-9]+(?:\\.[0-9]+)?(?:px|em|%)?");
        }
        return switch (key) {
            case "text-align" -> Set.of("left", "right", "center", "justify").contains(value);
            case "vertical-align" -> Set.of("top", "middle", "bottom", "baseline").contains(value);
            case "font-family" -> value.length() <= 150 && value.matches("[a-z0-9 ,\"'_-]+");
            case "font-weight" -> value.matches("normal|bold|[1-9]00");
            case "font-style" -> Set.of("normal", "italic").contains(value);
            case "text-decoration" -> Set.of("none", "underline", "line-through").contains(value);
            case "white-space" -> Set.of("normal", "pre-wrap", "pre-line").contains(value);
            default -> false;
        };
    }
}
