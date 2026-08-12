package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WorkPlanRespVO {
    private Long id;
    private String title;
    private String periodType;
    private Long planTypeId;
    private Long templateId;
    private Long templateVersionId;
    private Long ownerUserId;
    private Long ownerDeptId;
    private String objective;
    private String keyRequirements;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean summaryReady;
    private Long creatorUserId;
    private LocalDateTime publishedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
    private List<String> availableActions;
    private List<WorkPlanTemplateFieldSaveReqVO> fieldDefinitions;
    private Map<String, Object> planFields;
    private List<WorkTaskRespVO> tasks;
    private WorkPlanSummaryRespVO summary;
    private List<WorkPlanChangeRespVO> changes;
}
