package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class PositioningCardImportRespVO {
    private Long id;
    private Integer version;
    private Long templateId;
    private Long templateVersionId;
    private Integer templateVersionNo;
    private List<DirectorFormTemplateVO.Field> fields;
    private Map<String, Object> values;
    private Map<String, Object> dictSnapshots;
    private LocalDate trialEndDate;
    private Boolean professionalRisk;
    private List<String> skippedFieldKeys;
}
