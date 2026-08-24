package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PositioningCardRespVO {
    private Long id;
    private String cardNo;
    private Long accountId;
    private Long studentPersonId;
    private Long directorUserId;
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
