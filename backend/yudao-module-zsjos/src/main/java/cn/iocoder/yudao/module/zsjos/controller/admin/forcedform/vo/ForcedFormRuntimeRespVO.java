package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

import java.util.List;

@Data
public class ForcedFormRuntimeRespVO {

    private Long formId;
    private Long versionId;
    private Integer version;
    private String name;
    private String description;
    private Long recipientId;
    private Long batchId;
    private List<FieldVO> fields;

    @Data
    public static class FieldVO {
        private String key;
        private String type;
        private String label;
        private Boolean required;
        private String dictType;
        private Integer maxLength;
        private Integer maxCount;
        private Integer maxSizeMb;
        private List<String> allowedExtensions;
        private List<OptionVO> options;
    }

    @Data
    public static class OptionVO {
        private String label;
        private String value;
    }
}
