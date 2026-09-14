package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ContentReviewBatchRespVO {
    private Long id;
    private String batchNo;
    private Long accountId;
    private Long studentPersonId;
    private Long revisionOfBatchId;
    private List<Long> accountIds;
    private Long operatorUserId;
    private String operatorName;
    private Long directorUserId;
    private String directorName;
    private Map<String, Object> relationSnapshot;
    private Map<String, Object> contextSnapshot;
    private String status;
    private String currentStage;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String processInstanceId;
    private String currentTaskId;
    private String currentTaskKey;
    private LocalDateTime submittedAt;
    private LocalDateTime directorCompletedAt;
    private LocalDateTime finalCompletedAt;
    private LocalDateTime finalizedAt;
    private Integer version;
    private List<String> availableActions;
    private List<ContentReviewBatchItemRespVO> items;
}
