package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
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
            // Central-directory reading accepts valid STORED entries with data descriptors.
            // Reuse the Excel runtime's ZIP reader in memory; private plaintext never hits disk.
            try (var channel = new SeekableInMemoryByteChannel(bytes);
                 var zip = ZipFile.builder().setSeekableByteChannel(channel).get()) {
                int count = 0;
                boolean types = false, document = false;
                var entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    if (++count > 1000) throw exception(FIELD_INVALID, "文档内容过大");
                    var entry = entries.nextElement();
                    if (entry.getSize() > 20 * 1024 * 1024L) throw exception(FIELD_INVALID, "文档内容过大");
                    boolean required = "[Content_Types].xml".equals(entry.getName()) || "word/document.xml".equals(entry.getName());
                    if (!required || entry.isDirectory()) continue;
                    long expanded = 0; byte[] buffer = new byte[8192]; int read;
                    try (var content = zip.getInputStream(entry)) {
                        while ((read = content.read(buffer)) != -1) if ((expanded += read) > 20 * 1024 * 1024L)
                            throw exception(FIELD_INVALID, "文档内容过大");
                    }
                    types |= "[Content_Types].xml".equals(entry.getName());
                    document |= "word/document.xml".equals(entry.getName());
                }
                if (types && document) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } catch (IOException error) {
                throw exception(FIELD_INVALID, "DOCX 文档损坏或格式无效，请重新保存后上传");
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
