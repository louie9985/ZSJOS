package cn.iocoder.yudao.module.zsjos.service.file;

import java.util.Set;

/** Content-review files use one type contract at direct-upload and version-binding boundaries. */
public final class ContentAttachmentTypes {
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf", "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private ContentAttachmentTypes() { }

    public static boolean accepts(String type) {
        return type.startsWith("image/") || type.startsWith("video/") || DOCUMENT_TYPES.contains(type);
    }
}
