package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public final class FeedbackConfigVO {

    private FeedbackConfigVO() {
    }

    @Data
    public static class Resp {
        private String feedbackType;
        private Long formId;
        private String formName;
        private String titleFieldKey;
        private List<Long> dispatcherUserIds;
        private Boolean approvalEnabled;
        private String bpmProcessDefinitionKey;
        private Integer version;
        private List<String> incompatibleFields;
    }

    @Data
    public static class SaveReq {
        @NotBlank
        private String feedbackType;
        @NotNull
        private Long formId;
        @NotBlank
        @Size(max = 64)
        private String titleFieldKey;
        private List<Long> dispatcherUserIds;
        private Boolean approvalEnabled;
        @Size(max = 128)
        private String bpmProcessDefinitionKey;
        @NotNull
        private Integer version;
        @NotBlank
        @Size(max = 128)
        private String idempotencyKey;
    }

    @Data
    public static class UserOption {
        private Long id;
        private String nickname;
    }

    @Data
    public static class FormOption {
        private Long id;
        private String name;
        private List<String> incompatibleFields;
        private List<String> requiredTextFieldKeys;
        private List<String> requiredRatingFieldKeys;
    }

    @Data
    public static class ProcessOption {
        private String id;
        private String key;
        private String name;
        private Integer version;
    }
}
