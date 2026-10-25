package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class StudentInfoVO {
    private StudentInfoVO() {}
    @Data public static class Field {
        @NotBlank @Size(max=64) private String key;
        @NotBlank @Size(max=64) private String label;
        @NotBlank private String type;
        @NotNull private Boolean enabled;
        @NotNull private Boolean required;
        @NotNull @Min(0) @Max(10000) private Integer sort;
        @Size(max=500) private String note;
        @Size(max=100) private String dictType;
        private Boolean sensitive;
        @NotBlank private String group;
        @Min(2) @Max(9) private Integer maxImages;
        @Min(1) @Max(100) private Integer maxSizeMb;
        private List<String> allowedExtensions;
    }
    @Data public static class Config {
        private Version draft;
        private Version published;
        private List<Field> presets;
    }
    @Data public static class Version {
        private Long id;
        private Integer versionNo;
        private Integer revision;
        private String status;
        private LocalDateTime publishedAt;
        private List<Field> fields;
        private List<Long> educationCategoryIds;
    }
    @Data public static class Save {
        private Long id;
        @NotNull @Min(0) private Integer revision;
        @NotNull @Size(min=30,max=32) private List<@Valid Field> fields;
        @NotNull private List<Long> educationCategoryIds = List.of();
        private Long productId;
    }
    @Data public static class Publish {
        @NotNull private Long id;
        @NotNull private Integer revision;
    }
    @Data public static class Command {
        private Long formId;
        @NotNull private Integer version;
        @NotBlank @Size(max=64) private String idempotencyKey;
        private Boolean sent;
    }
    @Data public static class Submit {
        @NotNull @jakarta.validation.constraints.Min(0) private Long revision;
        @NotNull @Size(max=32) private Map<String, Object> values;
    }
    @Data public static class DraftWrite {
        @NotNull @jakarta.validation.constraints.Min(0) private Long revision;
        @NotNull @Size(max=32) private Map<String,String> values;
    }
    @Data public static class DraftSelection {
        private String code, label, dictType;
    }
    @Data public static class Draft {
        private long revision;
        private LocalDateTime savedAt;
        private Map<String,String> values = Map.of();
        private Map<String,DraftSelection> selections = Map.of();
    }
    @Data public static class Option {
        private String value;
        private String label;
        public Option(String value, String label) { this.value = value; this.label = label; }
    }
    @Data public static class Runtime {
        private Draft draft;
        private String status;
        private Long tenantId;
        private Integer configVersion;
        private String productName;
        private List<Image> images = List.of();
        private List<Field> fields = List.of();
        private Map<String,List<Option>> options = Map.of();
    }
    @Data public static class Detail {
        private Long id;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;
        private LocalDateTime expiresAt;
        private Integer configVersion;
        private List<Field> fields = List.of();
        private Map<String,String> values = Map.of();
        private Boolean canReadSensitive;
        private Boolean canExport;
        private List<Image> images = List.of();
    }
    @Data public static class Link {
        private Long formId;
        private String status;
        private String url;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private Boolean canRegenerate;
        private Boolean canRevoke;
        private Long sentByUserId;
        private LocalDateTime sentAt;
        private Integer registrationVersion;
    }
    @Data public static class Image {
        private Long id;
        private String fieldKey, side, contentType, name;
        private Long size;
    }
}
