package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class PositioningCardRespVO {
    private Long id;
    private String cardNo;
    private Long accountId;
    private Long studentPersonId;
    private Long serviceRelationId;
    private Long directorUserId;
    private Long operatorUserId;
    private Long templateId;
    private Long templateVersionId;
    private List<?> fieldsSnapshot;
    private Map<String, Object> valuesSnapshot;
    private Map<String, Object> dictSnapshot;
    private LocalDate trialEndDate;
    private Integer versionNo;
    private String layer1Json;
    private String layer2Json;
    private String formulaJson;
    private String feasibilityJson;
    private String contentFormJson;
    private String complianceJson;
    private Boolean professionalRisk;
    private String status;
    private String ipProcessInstanceId;
    private Long operatorReviewedByUserId;
    private LocalDateTime operatorReviewedAt;
    private String operatorReviewComment;
    private Integer version;
    private List<String> availableActions;
}
