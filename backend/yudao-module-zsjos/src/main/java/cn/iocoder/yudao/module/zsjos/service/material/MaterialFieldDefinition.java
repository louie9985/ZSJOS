package cn.iocoder.yudao.module.zsjos.service.material;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MaterialFieldDefinition {
    private String key;
    private String label;
    private String type;
    /** 页面列：账号详情、编导拆解或搭建建议。 */
    private String section;
    /** 列内展示分组名称。 */
    private String group;
    /** 阶段分组绑定的字典值，例如 s1-s6。 */
    private String stageCode;
    private Boolean required;
    private Boolean searchable;
    private Boolean multiple;
    private String recommendationDimension;
    private Boolean allowUnlimited;
    private String dictType;
    private Integer maxLength;
    private BigDecimal min;
    private BigDecimal max;
    private Integer minCount;
    private Integer maxCount;
    private Integer maxSizeMb;
    private List<String> allowedExtensions;
    private List<MaterialFieldDefinition> children;
    private Integer sort;
}
