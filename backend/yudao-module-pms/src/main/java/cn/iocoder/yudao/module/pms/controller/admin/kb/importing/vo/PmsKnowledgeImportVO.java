package cn.iocoder.yudao.module.pms.controller.admin.kb.importing.vo;

import cn.iocoder.yudao.module.pms.controller.admin.kb.library.vo.library.PmsKnowledgeLibrarySaveReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** Folder import wire contract. Paths are relative to the selected root, including its name. */
public final class PmsKnowledgeImportVO {
    @Data
    public static class Entry {
        @NotBlank @Size(max=2048) private String relativePath;
        @NotNull @Min(0) private Long fileSize;
        @NotNull @Min(0) private Long lastModified;
        @Pattern(regexp="[a-f0-9]{64}") private String digest;
    }
    @Data
    public static class Request {
        @NotBlank @Pattern(regexp="NEW|EXISTING") private String mode;
        @NotBlank @Size(max=64) private String requestKey;
        @NotBlank @Size(max=255) private String rootName;
        private Long libraryId;
        private Long folderId;
        @Valid private PmsKnowledgeLibrarySaveReqVO library;
        @NotEmpty @Size(max=1000) @Valid private List<Entry> files;
    }
    @Data
    public static class Resume {
        @NotNull private Long taskId;
        @NotEmpty @Size(max=1000) @Valid private List<Entry> files;
    }
    @Data
    public static class Item {
        private Long id;
        private String relativePath;
        private Long fileSize;
        private String status;
        private String reason;
        private Long documentId;
    }
    @Data
    public static class Task {
        private Long id;
        private Long libraryId;
        private Long folderId;
        private String rootName;
        private String mode;
        private String status;
        private int total;
        private long success;
        private long skipped;
        private long failed;
        private List<Item> items;
    }
}
