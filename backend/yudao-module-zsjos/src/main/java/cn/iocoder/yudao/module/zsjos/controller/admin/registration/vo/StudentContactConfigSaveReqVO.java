package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StudentContactConfigSaveReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
    @NotBlankIdempotency private String idempotencyKey;
    @NotNull @Min(5) @Max(10080) private Integer firstContactTimeoutMinutes;
    @NotNull @Min(5) @Max(43200) private Integer studyPlanTimeoutMinutes;
    @NotEmpty private List<@NotNull @Valid ChecklistItemReqVO> checklist;
    @NotNull @Size(max = 50) private List<@NotBlank @Size(max = 200) String> quickNotes;
    @NotNull private Map<@NotBlank String, @NotNull List<@NotBlank String>> collaboratorTabs;
    private Map<@NotBlank String, @NotNull List<@NotNull @Valid FormFieldReqVO>> forms;
    @Data public static class ChecklistItemReqVO {
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,63}$") private String key;
        @NotBlank @Size(max = 100) private String title;
        @NotBlank @Pattern(regexp = "checkbox|attachment") private String type;
        @NotNull private Boolean enabled;
        @NotNull private Boolean attachmentRequired;
        @NotNull private Integer sort;
    }
    @Data public static class FormFieldReqVO {
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,63}$") private String key;
        @NotBlank @Size(max = 100) private String title;
        @NotBlank @Pattern(regexp = "text|textarea|number|date|datetime|select|multi_select|radio|checkbox_group|checkbox|dict|attachment") private String type;
        @NotNull private Boolean required;
        @NotNull private Integer sort;
        @Size(max = 500) private String description;
        @Size(max = 100) private String dictType;
        private Boolean multiple;
    }
}
