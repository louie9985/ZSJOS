package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only summaries of the exact service. Restricted projections carry access state, never hidden data. */
@Data
public class StudentServiceSummaryVO {
    private String deliveryStage;
    private String deliveryStageLabel;
    private LocalDate examDate;
    private StudentContactContextRespVO.CurrentTaskVO currentTask;
    private StudentContactRecordRespVO latestContact;
    private String classNo;
    private Boolean systemClass;
    private String classDepartment;
    private String classExamSchedule;
    private LocalDateTime pausedAt;
    private String pauseReason;
    private LocalDateTime completedAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;
    private OrderHandoff orderHandoff;
    private InfoSubmission infoSubmission;

    @Data public static class OrderHandoff {
        private String access;
        private String orderType;
        private String status;
        private String formalSalesUserName;
        private String submitterUserName;
        private String formalOwnerIdentity;
        private LocalDateTime submittedAt;
        private LocalDateTime effectiveAt;
        private String studentNatureLabel;
        private String studentSourceLabel;
        private String classType;
        private String servicePeriodLabel;
        private String agreedExamTime;
        private String studentSpecialRequirements;
        private String remark;
        private String materialDeliveryContact;
    }
    @Data public static class InfoSubmission {
        private String access;
        private String status;
        private LocalDateTime submittedAt;
    }
}
