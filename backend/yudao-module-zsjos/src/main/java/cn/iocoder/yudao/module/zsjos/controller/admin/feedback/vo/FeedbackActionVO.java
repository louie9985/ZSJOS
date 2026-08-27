package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

public final class FeedbackActionVO {

    private FeedbackActionVO() {
    }

    @Data
    public static class VersionedCommand {
        @NotNull
        private Integer version;
        @NotBlank
        @Size(max = 128)
        private String idempotencyKey;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ResubmitReq extends VersionedCommand {
        @NotNull
        private Integer configVersion;
        @NotNull
        private Map<String, Object> values;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AssignReq extends VersionedCommand {
        @NotNull
        private Long assigneeUserId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReplyReq extends VersionedCommand {
        @NotBlank
        @Size(max = 5000)
        private String content;
        @Size(max = 20)
        private List<Long> attachmentIds;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CompleteReq extends VersionedCommand {
        @NotBlank
        @Size(max = 5000)
        private String result;
        @Size(max = 20)
        private List<Long> attachmentIds;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SurveySubmitReq extends VersionedCommand {
        @Schema(description = "满意度动态表单字段值")
        @NotNull
        private Map<String, Object> values;
    }
}
