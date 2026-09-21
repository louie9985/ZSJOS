package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 定位卡与定位访谈共用附件白名单。
 *
 * <p>访谈稿与定位卡附件允许文档、图片、音频、视频，但只接受扩展名与其真实内容一致的常见格式：
 * 单纯匹配扩展名会让伪装成 .png 的脚本被原样回放，因此这里同时校验 Tika 探测出的 MIME。
 */
public final class PositioningAttachmentTypes {

    /** 扩展名 -> 允许的 MIME。同一扩展名可对应多个 MIME（浏览器与 Tika 结果不同）。 */
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword", "application/x-tika-msoffice")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/x-tika-ooxml")),
            Map.entry("xls", Set.of("application/vnd.ms-excel", "application/x-tika-msoffice")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/x-tika-ooxml")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint", "application/x-tika-msoffice")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/x-tika-ooxml")),
            Map.entry("csv", Set.of("text/csv", "text/plain")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("md", Set.of("text/plain", "text/markdown", "text/x-web-markdown")),
            Map.entry("rtf", Set.of("application/rtf", "text/rtf", "text/plain")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("bmp", Set.of("image/bmp", "image/x-ms-bmp")),
            Map.entry("heic", Set.of("image/heic", "image/heif")),
            Map.entry("heif", Set.of("image/heif", "image/heic")),
            Map.entry("mp3", Set.of("audio/mpeg", "audio/mp3")),
            Map.entry("wav", Set.of("audio/x-wav", "audio/wav", "audio/vnd.wave")),
            Map.entry("m4a", Set.of("audio/mp4", "audio/x-m4a", "video/mp4")),
            Map.entry("aac", Set.of("audio/aac", "audio/x-aac", "audio/mp4")),
            Map.entry("flac", Set.of("audio/flac", "audio/x-flac")),
            Map.entry("ogg", Set.of("audio/ogg", "application/ogg", "video/ogg")),
            Map.entry("amr", Set.of("audio/amr", "audio/3gpp")),
            Map.entry("mp4", Set.of("video/mp4", "audio/mp4")),
            Map.entry("mov", Set.of("video/quicktime")),
            Map.entry("webm", Set.of("video/webm", "audio/webm")),
            Map.entry("mkv", Set.of("video/x-matroska", "video/webm")),
            Map.entry("avi", Set.of("video/x-msvideo", "video/avi")));

    /** 与前端 accept 列表同源的扩展名集合。 */
    public static final Set<String> EXTENSIONS = ALLOWED.keySet();

    private static final Set<String> GENERIC = Set.of("application/octet-stream", "application/x-tika-msoffice",
            "application/x-tika-ooxml", "application/zip");

    /** 纯文本格式没有可靠魔数，Tika 对无 BOM 的 txt/md/csv 常报 octet-stream。 */
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("txt", "md", "csv", "rtf");

    private PositioningAttachmentTypes() {
    }

    /**
     * 校验文件名与内容是否为受支持的附件。
     *
     * @param name 原始文件名
     * @param data 文件内容
     * @return 通过时为 Tika 探测到的 MIME；不通过时返回 null
     */
    public static String detectAllowed(String name, byte[] data) {
        if (name == null || data == null || data.length == 0) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        Set<String> candidates = ALLOWED.get(extension);
        if (candidates == null) return null;
        String detected = FileTypeUtils.getMineType(data, name);
        if (detected == null || detected.isBlank()) return null;
        detected = detected.toLowerCase(Locale.ROOT);
        if (candidates.contains(detected)) return detected;
        // 压缩容器格式（docx/xlsx/pptx/odf）Tika 常统一报 zip，交给扩展名白名单兜底。
        if (GENERIC.contains(detected) && candidates.stream().anyMatch(type -> type.contains("officedocument")
                || type.contains("tika-ooxml") || type.contains("tika-msoffice") || type.equals("application/zip"))) {
            return detected;
        }
        if (PLAIN_TEXT_EXTENSIONS.contains(extension) && isPlainText(data)) return detected;
        return null;
    }

    /** 拒绝对浏览器来说可执行的内容：无控制字节即视作文本。 */
    private static boolean isPlainText(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int index = 0; index < limit; index++) {
            int value = data[index] & 0xFF;
            if (value == 0) return false;
            if (value < 0x09 || (value > 0x0D && value < 0x20)) return false;
        }
        return true;
    }

    /** 排除上传文件名里可能污染存储路径的字符。 */
    public static String storageName(String name) {
        return Objects.requireNonNullElse(name, "attachment").replaceAll("[\\\\/\\r\\n]", "_");
    }
}
