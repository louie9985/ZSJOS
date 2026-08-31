package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountMaintenanceProblemVO;
import java.time.LocalDate;

@Data
public class MediaStudentDetailRespVO {
    private MyStudentRespVO student;
    private List<AccountVO> accounts;
    private List<PositioningVO> positioningCards;
    private List<PositioningVO> positioningDrafts;
    private List<ContentVO> contents;
    private List<TicketVO> productionTickets;
    private List<OperationVO> operationTimeline;
    private List<TaskStageVO> studentTaskLine;
    /** Compatibility projection for older workbench clients; new clients use studentTaskLine and account.taskLine. */
    private List<TaskStageVO> taskLine;
    private PendingStatsVO pendingStats;

    @Data
    public static class AccountVO {
        private Long id;
        private String accountNo;
        private String nickname;
        private String platformLabel;
        private String stage;
        private String stageLabelSnapshot;
        private String currentStatusValue;
        private String currentStatusLabelSnapshot;
        private List<MediaAccountMaintenanceProblemVO> primaryProblems;
        private String executionMeasureValue;
        private String executionMeasureLabelSnapshot;
        private String adjustmentDirection;
        private LocalDate maintenanceStartDate;
        private LocalDate maintenanceEndDate;
        private String runStatus;
        private Integer version;
        private LocalDateTime lastActivityAt;
        private List<String> availableActions;
        private List<MediaAccountDetailSnapshotVO> detailSnapshots;
        private List<TaskStageVO> taskLine;
    }

    @Data
    public static class PositioningVO {
        private Long id;
        private Long accountId;
        private String cardNo;
        private Long submissionId;
        private String status;
        private Integer versionNo;
        private Integer submissionNo;
        private LocalDateTime submittedAt;
        private LocalDateTime studentDecidedAt;
        private String studentDecision;
        private String studentDecisionComment;
        private Boolean latestRound;
        private Boolean effective;
        /** Compatibility alias for latestRound. */
        private Boolean current;
        private Boolean professionalRisk;
        private Integer version;
        private LocalDateTime lastActivityAt;
        private List<String> availableActions;
    }

    @Data
    public static class ContentVO {
        private Long id;
        private Long accountId;
        private String contentNo;
        private String title;
        private String status;
        private Integer currentVersionNo;
        private LocalDateTime publishedAt;
        private Integer version;
        private LocalDateTime lastActivityAt;
        private List<String> availableActions;
    }

    @Data
    public static class TicketVO {
        private Long id;
        private Long accountId;
        private String ticketNo;
        private String status;
        private LocalDateTime deadlineAt;
        private Integer revisionCount;
        private LocalDateTime lastActivityAt;
    }

    @Data
    public static class OperationVO {
        private String key;
        private String type;
        private String title;
        private String detail;
        private String operatorName;
        private LocalDateTime occurredAt;
    }

    @Data
    public static class TaskStageVO {
        private String key;
        private String label;
        private String status;
        private String detail;
    }

    @Data
    public static class PendingStatsVO {
        private Integer accountCount;
        private Integer positioningCount;
        private Integer contentCount;
        private Integer productionCount;
    }
}
