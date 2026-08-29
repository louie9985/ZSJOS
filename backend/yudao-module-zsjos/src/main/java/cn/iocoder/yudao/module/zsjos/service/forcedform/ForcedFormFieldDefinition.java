package cn.iocoder.yudao.module.zsjos.service.forcedform;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ForcedFormFieldDefinition {

    private String key;
    private String type;
    private String label;
    private Boolean required;
    private String dictType;
    private Integer maxLength;
    private Integer maxCount;
    private Integer maxSizeMb;
    private List<String> allowedExtensions;
    private List<Map<String, Object>> options;

}
