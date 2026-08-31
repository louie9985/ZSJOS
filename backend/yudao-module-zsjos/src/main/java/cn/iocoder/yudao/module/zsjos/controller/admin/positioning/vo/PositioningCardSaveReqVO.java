package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class PositioningCardSaveReqVO {
    @NotNull private Long accountId;
    private Long studentPersonId;
    private Long serviceRelationId;
    private Long templateId;
    private LocalDate trialEndDate;
    private Map<String, Object> values;
    private Integer version;
    private Boolean professionalRisk;
    private String layer1Json;
    private String layer2Json;
    private String formulaJson;
    private String feasibilityJson;
    private String contentFormJson;
    private String complianceJson;
}
