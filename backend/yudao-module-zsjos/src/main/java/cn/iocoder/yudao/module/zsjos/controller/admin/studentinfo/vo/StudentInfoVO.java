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
    }
    @Data public static class Save {
        private Long id;
        @NotNull @Min(0) private Integer revision;
        @NotNull @Size(min=16,max=16) @Valid private List<Field> fields;
    }
    @Data public static class Publish {
        @NotNull private Long id;
        @NotNull private Integer revision;
    }
    @Data public static class Command {
        @NotNull private Long formId;
    }
    @Data public static class Submit {
        @NotNull @Size(max=16) private Map<String, Object> values;
    }
    @Data public static class Option {
        private String value;
        private String label;
        public Option(String value, String label) { this.value = value; this.label = label; }
    }
    @Data public static class Runtime {
        private String status;
        private Long tenantId;
        private Integer configVersion;
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
    }
    @Data public static class Link {
        private Long formId;
        private String status;
        private String url;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private Boolean canRegenerate;
        private Boolean canRevoke;
    }
}
