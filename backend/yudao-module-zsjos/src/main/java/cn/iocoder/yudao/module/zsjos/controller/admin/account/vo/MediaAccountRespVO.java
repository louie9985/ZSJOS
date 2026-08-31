package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class MediaAccountRespVO {
    private Long id;
    private String accountNo;
    private Long studentPersonId;
    private String ownershipType;
    private Long ownerOperatorUserId;
    private Long directorUserId;
    private String platformValue;
    private String platformLabelSnapshot;
    private String platformAccountId;
    private String nickname;
    private Long detailConfigVersionId;
    private Map<String, Object> detailValues;
    private List<MediaAccountDetailSnapshotVO> detailSnapshots;
    private String leadDirection;
    private String sStage;
    private String currentStatusValue;
    private String currentStatusLabelSnapshot;
    private String sStageLabelSnapshot;
    private List<MediaAccountMaintenanceProblemVO> primaryProblems;
    private String executionMeasureValue;
    private String executionMeasureLabelSnapshot;
    private String adjustmentDirection;
    private LocalDate maintenanceStartDate;
    private LocalDate maintenanceEndDate;
    private LocalDateTime sStageEnteredAt;
    private String runStatus;
    private String rescueStatus;
    private String rebindProcessInstanceId;
    private Long rebindTargetStudentPersonId;
    private Long rebindReviewerUserId;
    private String rebindStatus;
    private String rebindResultReason;
    private Integer version;
    private List<String> availableActions;
}
