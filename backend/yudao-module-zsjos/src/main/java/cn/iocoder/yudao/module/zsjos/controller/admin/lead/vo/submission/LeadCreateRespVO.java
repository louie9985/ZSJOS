package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import lombok.Data;

@Data
public class LeadCreateRespVO {
    private Long leadId;
    private Long reviewId;
    private String outcome;
    private String assignmentStatus;
    private Long pendingAssigneeUserId;
    private String existingLeadStatus;
    private String existingQualificationStatus;
    private String existingOperationalStatus;

    public LeadCreateRespVO(Long leadId, String outcome, String assignmentStatus, Long pendingAssigneeUserId) {
        this.leadId = leadId;
        this.outcome = outcome;
        this.assignmentStatus = assignmentStatus;
        this.pendingAssigneeUserId = pendingAssigneeUserId;
    }

    public static LeadCreateRespVO reviewPending(Long reviewId) {
        LeadCreateRespVO response = new LeadCreateRespVO(null, "review_pending", null, null);
        response.setReviewId(reviewId);
        return response;
    }

    public static LeadCreateRespVO duplicateRejected(Long leadId, String status,
                                                     String qualificationStatus, String operationalStatus) {
        LeadCreateRespVO response = new LeadCreateRespVO(leadId, "duplicate_rejected", null, null);
        response.setExistingLeadStatus(status);
        response.setExistingQualificationStatus(qualificationStatus);
        response.setExistingOperationalStatus(operationalStatus);
        return response;
    }
}
