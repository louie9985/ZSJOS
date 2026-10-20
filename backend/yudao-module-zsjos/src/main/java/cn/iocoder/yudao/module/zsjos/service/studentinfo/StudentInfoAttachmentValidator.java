package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

/** Field policy and byte validation apply equally to public and internal uploads. */
public final class StudentInfoAttachmentValidator {
    private StudentInfoAttachmentValidator() { }
    public static String validate(byte[] bytes, String name, List<String> allowed) throws Exception {
        String ext = name == null ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) throw exception(FIELD_INVALID, "附件格式不符合此字段要求");
        if (Set.of("jpg", "jpeg", "png").contains(ext)) return StudentInfoImageService.validateImage(bytes, name);
        if ("pdf".equals(ext) && bytes.length > 8 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
            return "application/pdf";
        if ("doc".equals(ext) && bytes.length >= 8 && java.util.HexFormat.of().formatHex(Arrays.copyOf(bytes, 8)).equals("d0cf11e0a1b11ae1"))
            return "application/msword";
        if ("docx".equals(ext)) {
            boolean types = false, document = false; int count = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (++count > 1000) throw exception(FIELD_INVALID, "文档内容过大");
                    if (entry.getSize() > 20 * 1024 * 1024L) throw exception(FIELD_INVALID, "文档内容过大");
                    types |= "[Content_Types].xml".equals(entry.getName());
                    document |= "word/document.xml".equals(entry.getName());
                    if (types && document) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    long expanded = 0; byte[] buffer = new byte[8192]; int read;
                    while ((read = zip.read(buffer)) != -1) if ((expanded += read) > 20 * 1024 * 1024L)
                        throw exception(FIELD_INVALID, "文档内容过大");
                }
            }
        }
        throw exception(FIELD_INVALID, "附件内容与扩展名不符");
    }
    public static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png"; case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> ".jpg";
        };
    }
}
