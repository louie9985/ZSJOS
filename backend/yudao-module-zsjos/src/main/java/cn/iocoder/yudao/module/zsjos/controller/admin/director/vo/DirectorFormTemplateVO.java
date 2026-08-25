package cn.iocoder.yudao.module.zsjos.controller.admin.director.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DirectorFormTemplateVO {
    private DirectorFormTemplateVO() {}

    @Data public static class TemplateResp {
        private Long id; private String scene; private String templateCode; private String name;
        private Boolean defaultTemplate; private String status; private Integer version;
        private VersionResp published; private VersionResp draft; private List<VersionResp> versions;
    }
    @Data public static class VersionResp {
        private Long id; private Long templateId; private Integer versionNo; private String status;
        private List<Field> fields; private Long publishedByUserId; private LocalDateTime publishedAt; private Integer version;
    }
    @Data public static class Field {
        @NotBlank @Pattern(regexp = "^[a-z][a-zA-Z0-9_]{1,63}$") private String key;
        @NotBlank @Size(max = 100) private String title;
        @NotBlank @Pattern(regexp = "text|textarea|number|date|datetime|select|multi_select|radio|checkbox_group|checkbox|attachment|region") private String type;
        @NotNull private Boolean enabled;
        @NotNull private Boolean required;
        @NotNull private Boolean systemField;
        @NotNull private Integer sort;
        @Size(max = 500) private String description;
        @Size(max = 100) private String dictType;
        private Boolean multiple;
        private Integer minSelections;
        private Integer maxSelections;
        private Integer minValue;
        private Integer maxValue;
        private Integer maxLength;
        @Size(max = 64) private String group;
    }
    @Data public static class CreateReq {
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9_]{2,63}$") private String templateCode;
        @NotBlank @Size(max = 100) private String name;
        @NotNull private Boolean defaultTemplate;
        @NotEmpty private List<@NotNull @Valid Field> fields;
    }
    @Data public static class SaveDraftReq {
        @NotNull private Long versionId;
        @NotNull private Integer version;
        @NotBlank @Size(max = 100) private String name;
        @NotNull private Boolean defaultTemplate;
        @NotEmpty private List<@NotNull @Valid Field> fields;
    }
    @Data public static class CopyReq {
        @NotNull private Long templateId;
        @NotNull private Integer version;
    }
    @Data public static class PublishReq {
        @NotNull private Long versionId;
        @NotNull private Integer version;
    }
    @Data public static class Snapshot {
        private Long templateId; private Long templateVersionId; private Integer templateVersionNo;
        private List<Field> fields; private Map<String, Object> values; private Map<String, Object> dictSnapshots;
    }
}
