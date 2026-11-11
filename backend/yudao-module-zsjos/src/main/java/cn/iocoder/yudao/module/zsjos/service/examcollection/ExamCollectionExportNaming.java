package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionExportVO.Settings;
import java.text.Normalizer;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

/** Ordered tokens are an export protocol. Business labels come from task snapshots. */
public final class ExamCollectionExportNaming {
    private ExamCollectionExportNaming() {}
    private static final Set<String> TOKENS = Set.of("name", "mobile", "idCard", "material", "side", "sequence", "original", "submittedAt");
    public static void validate(Settings settings, Set<String> fields) {
        if (settings == null || settings.getLayout() == null || settings.getNaming() == null || settings.getOverrides() == null
                || settings.getOverrides().size() > 100 || !fields.containsAll(settings.getOverrides().keySet())) invalid();
        validateTokens(settings.getNaming());
        settings.getOverrides().values().forEach(ExamCollectionExportNaming::validateTokens);
    }
    private static void validateTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty() || tokens.size() > 12) invalid();
        for (String token : tokens) {
            if (token == null || token.length() > 80 || !(TOKENS.contains(token) || token.startsWith("text:") && !token.substring(5).isBlank())) invalid();
        }
    }
    private static void invalid() { throw exception(FIELD_INVALID, "导出命名规则"); }
    public static String name(List<String> tokens, Map<String,String> values) {
        var parts = new ArrayList<String>();
        for (String token : tokens) {
            String value = token.startsWith("text:") ? token.substring(5) : values.getOrDefault(token, "");
            if (value != null && !value.isBlank()) parts.add(value);
        }
        return safe(String.join("_", parts));
    }
    public static String safe(String value) {
        String result = Normalizer.normalize(Objects.requireNonNullElse(value, ""), Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_").replaceAll("^[ .]+|[ .]+$", "");
        if (result.isBlank()) result = "未命名";
        if (result.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) result = "_" + result;
        return result.substring(0, result.offsetByCodePoints(0, Math.min(100, result.codePointCount(0, result.length()))));
    }
    public static String extension(String type) {
        return switch (Objects.requireNonNullElse(type, "")) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> throw exception(FIELD_INVALID, "不支持导出的材料类型");
        };
    }
    public static String unique(String directory, String name, String extension, Set<String> used) {
        String candidate = directory + name + extension;
        int index = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) candidate = directory + name + "_" + index++ + extension;
        return candidate;
    }
}
