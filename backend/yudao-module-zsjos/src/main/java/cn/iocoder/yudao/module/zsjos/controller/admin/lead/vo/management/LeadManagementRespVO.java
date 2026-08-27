package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadManagementRespVO {

    private Long id;
    private String leadNo;
    private Long personId;
    private String submittedName;
    private String submittedMobile;
    private String submittedWechatId;
    private String sourceType;
    private String sourceLabel;
    private Long sourceUserId;
    private String sourceUserName;
    private String partnerOwnerNameSnapshot;
    private String providerOwnerType;
    private Long providerOwnerId;
    private String providerOwnerNameSnapshot;
    private Long contributionUserIdSnapshot;
    private String contributionUserNameSnapshot;
    private Long contributionSupervisorUserIdSnapshot;
    private String contributionSupervisorNameSnapshot;
    private Long contributionDeptIdSnapshot;
    private String contributionDeptNameSnapshot;
    private LocalDateTime countedAt;
    private String sourceChannel;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String leadCategory;
    private String leadCategoryLabelSnapshot;
    private String remark;
    private String status;
    private String assignmentStatus;
    private String handlingStage;
    private String qualificationStatus;
    private String followUpStatus;
    private String operationalStatus;
    private String dispatchMode;
    private Long ownerUserId;
    private String ownerUserName;
    private Long pendingAssigneeUserId;
    private String pendingAssigneeUserName;
    private LocalDateTime pendingExpiresAt;
    private Integer assignmentAttemptCount;
    private LocalDateTime publicPoolAt;
    private LocalDateTime submittedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime nextFollowUpAt;
    private LocalDateTime currentAssignmentFirstFollowUpAt;
    private LocalDateTime currentAssignmentFirstFollowUpDeadlineAt;
    private LocalDateTime qualificationStartedAt;
    private LocalDateTime qualificationDeadlineAt;
    private LocalDateTime suspendedAt;
    private Long qualifiedByUserId;
    private String qualifiedByUserName;
    private LocalDateTime qualifiedAt;
    private String validDescription;
    private LocalDateTime convertedAt;
    private LocalDateTime salesOrderSubmittedAt;
    private String invalidReason;
    private String invalidReasonLabelSnapshot;
    private String invalidDescription;
    private List<LeadEvidenceVO> invalidEvidence;
    private Long recycleSourceOwnerUserId;
    private String recycleSourceOwnerUserName;
    private LocalDateTime appealDeadlineAt;
    private LocalDateTime closedAt;
    private String closeReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<String> relationTypes;
    private Boolean overviewVisible;
    private List<String> visibleTabs;
    private String identityMaskMode;
    private LeadProductVO primaryProduct;
    private List<LeadProductVO> intendedProducts;
    private List<LeadAttachmentVO> attachments;
    private OpportunityVO opportunity;
    private Long activeSalesOrderId;
    private String activeSalesOrderStatus;
    private List<ActionVO> availableActions;

    @Data
    public static class OpportunityVO {
        private Long id;
        private String status;
        private LocalDateTime nextFollowUpAt;
        private LocalDateTime wonAt;
    }

    @Data
    public static class ActionVO {
        private String code;
        private Boolean enabled;

        public ActionVO(String code, Boolean enabled) {
            this.code = code;
            this.enabled = enabled;
        }
    }

    @Data
    public static class LeadProductVO {
        private Long id;
        private String spuRef;
        private String spuName;
        private String skuRef;
        private String skuName;
        private String selectedAttrValues;
        private BigDecimal price;
        private String categoryName;
        private Boolean primary;
    }

    @Data
    public static class LeadAttachmentVO {
        private Long id;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
    }

    @Data
    public static class LeadEvidenceVO {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Integer sort;
    }
}
