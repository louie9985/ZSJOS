package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public interface StudentArchiveExtraVO {
    record Field(String key, String label, String type, List<String> allowedExtensions) { }
    @Data class Value {
        @Size(max=2000) private String text = "";
        @Size(max=9) private List<@NotBlank @Size(max=2000) String> links = List.of();
        @Size(max=9) private List<@NotNull Long> attachmentIds = List.of();
    }
    @Data class Detail {
        private Integer revision = 0;
        private Integer visibilityRevision = 0;
        private Boolean canMaintain, canReadAttachments;
        private Boolean canConfigureVisibility;
        private List<Field> fields = List.of();
        private Map<String,Value> values = Map.of();
        private Map<String,Boolean> educationVisibility = Map.of();
        private List<StudentInfoVO.Image> attachments = List.of();
        private List<StudentArchiveVO.History> history = List.of();
    }
    @Data class Save {
        @NotNull @Min(0) private Integer revision;
        @NotBlank @Size(max=64) private String idempotencyKey;
        @Size(max=500) private String reason;
        @NotNull @Size(min=1,max=11) private Map<String,@Valid Value> changes;
    }
    @Data class VisibilitySave {
        @NotNull @Min(0) private Integer revision;
        @NotNull @Size(min=11,max=11) private Map<String,@NotNull Boolean> visibility;
    }
}
