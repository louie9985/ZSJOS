package cn.iocoder.yudao.module.system.service.notice;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Safe table presentation only; never persist this projection over the editor's source HTML. */
final class NoticeShareTables {
    private static final Set<String> DIMENSIONS = Set.of("width", "min-width", "max-width", "height", "min-height", "max-height");
    private static final Set<String> EDGES = Set.of("border", "border-top", "border-right", "border-bottom", "border-left");

    private NoticeShareTables() {}

    static String cleanStyle(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String declaration : input.split(";")) {
            String[] pair = declaration.split(":", 2);
            if (pair.length != 2) continue;
            String property = pair[0].trim().toLowerCase(Locale.ROOT);
            String value = pair[1].trim().toLowerCase(Locale.ROOT);
            if (allowed(property, value)) result.put(property, value);
        }
        return style(result);
    }

    private static boolean allowed(String property, String value) {
        if (DIMENSIONS.contains(property)) return length(value, true) || value.equals("auto");
        if (EDGES.contains(property)) {
            if (Set.of("none", "0").contains(value)) return true;
            // rgb/rgba may contain spaces: the first two terms are the width and line style.
            String[] parts = value.split("\\s+", 3);
            return parts.length == 3 && length(parts[0], false) && lineStyle(parts[1]) && color(parts[2]);
        }
        if (property.matches("border(?:-(?:top|right|bottom|left))?-width")) return length(value, false);
        if (property.matches("border(?:-(?:top|right|bottom|left))?-style")) return lineStyle(value);
        if (property.matches("border(?:-(?:top|right|bottom|left))?-color")) return color(value);
        return switch (property) {
            case "color", "background-color", "background" -> color(value);
            case "padding", "padding-top", "padding-right", "padding-bottom", "padding-left" -> lengths(value);
            case "table-layout" -> Set.of("auto", "fixed").contains(value);
            case "border-collapse" -> Set.of("collapse", "separate").contains(value);
            case "border-spacing" -> lengths(value);
            case "text-align" -> Set.of("left", "right", "center", "justify", "start", "end").contains(value);
            case "vertical-align" -> Set.of("top", "middle", "bottom", "baseline").contains(value);
            case "font-weight" -> value.matches("normal|bold|[1-9]00");
            case "font-style" -> Set.of("normal", "italic", "oblique").contains(value);
            case "font-size" -> length(value, true);
            case "text-decoration" -> Set.of("none", "underline", "line-through").contains(value);
            case "white-space" -> Set.of("normal", "nowrap", "pre-wrap", "pre-line").contains(value);
            default -> false;
        };
    }

    private static boolean length(String value, boolean percent) {
        if (!value.matches("(?:0|[0-9]+(?:\\.[0-9]+)?)(?:px|%)?")) return false;
        if (value.endsWith("%") && !percent) return false;
        double number = Double.parseDouble(value.replace("px", "").replace("%", ""));
        return number <= (value.endsWith("%") ? 100 : 10000)
                && (number == 0 || value.endsWith("px") || value.endsWith("%"));
    }

    private static boolean lengths(String value) {
        String[] parts = value.split("\\s+");
        return parts.length <= 4 && java.util.Arrays.stream(parts).allMatch(part -> length(part, false));
    }

    private static boolean lineStyle(String value) {
        return Set.of("none", "solid", "dashed", "dotted", "double").contains(value);
    }

    private static boolean color(String value) {
        // No escapes, comments, variables, URLs or arbitrary CSS functions can enter the page.
        if (value.matches("#[0-9a-f]{3}(?:[0-9a-f](?:[0-9a-f]{2}){0,2})?")) return true;
        if (value.matches("[a-z]{1,24}")) return true; // Unknown color keywords are ignored by the browser.
        return value.matches("rgba?\\(\\s*[0-9.]+%?\\s*,\\s*[0-9.]+%?\\s*,\\s*[0-9.]+%?(?:\\s*,\\s*[0-9.]+%?)?\\s*\\)");
    }

    static void prepare(Document document) {
        for (Element table : document.select("table")) {
            double minimum = pixels(table);
            var columns = table.select("col").stream().filter(col -> col.closest("table") == table).toList();
            if (!columns.isEmpty()) {
                minimum = Math.max(minimum, columns.stream().mapToDouble(col ->
                        (pixels(col) > 0 ? pixels(col) : 80) * span(col, "span")).sum());
            }
            for (Element row : table.select("tr")) {
                if (row.closest("table") != table) continue;
                double rowWidth = 0;
                for (Element cell : row.children()) {
                    if (!Set.of("td", "th").contains(cell.normalName())) continue;
                    double width = pixels(cell);
                    if (width > 0) minimumStyle(cell, width);
                    rowWidth += width > 0 ? width : 80 * span(cell, "colspan");
                }
                if (columns.isEmpty()) minimum = Math.max(minimum, rowWidth);
            }
            if (minimum > 0) minimumStyle(table, minimum);
            table.wrap("<div class=\"notice-table-scroll\" tabindex=\"0\" role=\"region\" aria-label=\"正文表格，可横向滚动\"></div>");
        }
    }

    private static int span(Element element, String attribute) {
        try { return Math.max(1, Math.min(1000, Integer.parseInt(element.attr(attribute)))); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static Map<String, String> declarations(Element element) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String declaration : element.attr("style").split(";")) {
            String[] pair = declaration.split(":", 2);
            if (pair.length == 2) values.put(pair[0].trim(), pair[1].trim());
        }
        return values;
    }

    private static double pixels(Element element) {
        String value = declarations(element).getOrDefault("width", element.attr("width"));
        return value.matches("[0-9]+(?:\\.[0-9]+)?(?:px)?") ? Math.min(10000, Double.parseDouble(value.replace("px", ""))) : 0;
    }

    private static void minimumStyle(Element element, double minimum) {
        Map<String, String> values = declarations(element);
        if (!values.containsKey("min-width")) values.put("min-width", String.format(Locale.ROOT, "%s", minimum) + "px");
        element.attr("style", style(values));
    }

    private static String style(Map<String, String> values) {
        return String.join(";", values.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList());
    }
}
