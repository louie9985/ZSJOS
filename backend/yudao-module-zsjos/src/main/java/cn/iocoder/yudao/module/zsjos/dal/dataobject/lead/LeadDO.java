package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead")
@KeySequence("zsjos_lead_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadDO extends TenantBaseDO {
    @TableId private Long id;
    private String leadNo;
    private Long personId;
    /** Original identity scope, populated before merging; null means the current Person. */
    private Long identityOriginPersonId;
    private String submittedName;
    private String submittedMobile;
    private String submittedWechatId;
    private String sourceType;
    /** Original submission entry, independent of the business acquisition source and later ownership. */
    private String submissionSourceType;
    private Long sourceUserId;
    /** 销售/教务录单时关联的新媒体提供方；只有明确记录标记为 true 才能解释空值。 */
    private Long sourceProviderUserId;
    private Boolean sourceProviderRecorded;
    private Long sourceDeptId;
    private Long partnerId;
    private Long partnerOwnerUserIdSnapshot;
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
    private String sourceChannelId;
    private String sourceChannelLabelSnapshot;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String salesStage;
    private String salesStageLabelSnapshot;
    private String leadCategory;
    private String leadCategoryLabelSnapshot;
    private String remark;
    private String status;
    private String assignmentStatus;
    private String dispatchMode;
    private Long ownerUserId;
    private String ownerIdentity;
    private String pendingOwnerIdentity;
    private LocalDateTime ownershipStartedAt;
    private Long currentAssignmentHistoryId;
    private LocalDateTime currentAssignmentFirstFollowUpAt;
    private LocalDateTime currentAssignmentFirstFollowUpDeadlineAt;
    private LocalDateTime lastFollowUpAt;
    private Long lastFollowUpRecordId;
    private LocalDateTime nextFollowUpAt;
    private LocalDateTime noProgressWarnedAt;
    private Integer followUpCount;
    private Integer qualificationRoundNo;
    private LocalDateTime qualificationStartedAt;
    private LocalDateTime qualificationDeadlineAt;
    private String qualificationRuleSnapshot;
    private LocalDateTime suspendedAt;
    private Long qualifiedByUserId;
    private LocalDateTime qualifiedAt;
    private String validDescription;
    private String invalidReasonLabelSnapshot;
    private String invalidDescription;
    private String invalidEvidenceRefs;
    private Long recycleSourceOwnerUserId;
    private Long pendingAssigneeUserId;
    private LocalDateTime pendingExpiresAt;
    private Integer assignmentAttemptCount;
    private String assignmentRuleSnapshot;
    private LocalDateTime publicPoolAt;
    private String submissionIdempotencyKey;
    private LocalDateTime submittedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime convertedAt;
    private String invalidReason;
    private LocalDateTime appealDeadlineAt;
    private LocalDateTime closedAt;
    private String closeReason;
    private Integer version;
}
