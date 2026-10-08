package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ExamNoteErrorCodes.*;

public final class ExamCalendarNoteContent {
    private ExamCalendarNoteContent() {}
    private static final Pattern IMAGE = Pattern.compile("exam-note-image:([1-9][0-9]{0,18})");
    private static final Pattern COLOR = Pattern.compile("(?i)(#[0-9a-f]{3,8}|[a-z]{1,20}|rgba?\\([0-9.,% ]{1,40}\\))");
    public record Cleaned(String html, Set<Long> fileIds) {}

    public static Cleaned clean(String input) {
        if (input == null || input.getBytes(StandardCharsets.UTF_8).length > 204800) throw exception(CONTENT_INVALID);
        Document doc = Jsoup.parseBodyFragment(input);
        doc.select("script,style,iframe,object,embed,svg,math,form").remove();
        if (doc.text().codePointCount(0, doc.text().length()) > 5000 || doc.select("img").size() > 20) throw exception(CONTENT_INVALID);
        Set<Long> ids = new LinkedHashSet<>();
        for (var image : doc.select("img")) {
            var match = IMAGE.matcher(image.attr("src"));
            if (!match.matches()) throw exception(IMAGE_INVALID);
            try { ids.add(Long.valueOf(match.group(1))); }
            catch (NumberFormatException invalid) { throw exception(IMAGE_INVALID); }
        }
        for (var element : doc.select("[style]")) {
            List<String> allowed = new ArrayList<>();
            for (String declaration : element.attr("style").split(";")) {
                String[] pair = declaration.split(":", 2);
                if (pair.length != 2) continue;
                String key = pair[0].trim().toLowerCase(Locale.ROOT), value = pair[1].trim();
                if ((Set.of("color", "background-color").contains(key) && COLOR.matcher(value).matches())
                    || (key.equals("text-align") && Set.of("left", "right", "center", "justify").contains(value))) {
                    allowed.add(key + ":" + value);
                }
            }
            element.attr("style", String.join(";", allowed));
        }
        Safelist safe = new Safelist().addTags("p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "strong", "b", "em", "i", "u", "s", "span", "ul", "ol", "li", "a", "blockquote", "table", "thead", "tbody", "tr", "td", "th", "img")
            .addAttributes(":all", "style").addAttributes("a", "href").addProtocols("a", "href", "http", "https", "mailto")
            .addAttributes("img", "src", "alt").addProtocols("img", "src", "exam-note-image")
            .addAttributes("td", "colspan", "rowspan").addAttributes("th", "colspan", "rowspan");
        String html = Jsoup.clean(doc.body().html(), "", safe, new Document.OutputSettings().prettyPrint(false));
        if (Jsoup.parseBodyFragment(html).text().isBlank() && ids.isEmpty()) html = "";
        return new Cleaned(html, ids);
    }
}
