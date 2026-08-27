package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "反馈动态表单 Response VO")
@Data
public class FeedbackFormRespVO {

    private String feedbackType;
    private Long formId;
    private String formName;
    private String titleFieldKey;
    private Integer configVersion;
    private Boolean open;
    private String unavailableReason;
    private List<Field> fields;

    @Data
    public static class Field {
        private String key;
        private String label;
        private String type;
        private Boolean required;
        private String dictionaryType;
        private Integer maxRating;
        private Integer maxLength;
        private List<Option> options;
    }

    @Data
    public static class Option {
        private String value;
        private String label;
    }
}
