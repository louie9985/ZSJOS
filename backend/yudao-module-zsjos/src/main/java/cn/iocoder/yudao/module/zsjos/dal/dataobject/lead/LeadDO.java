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
    private String submittedName;
    private String submittedMobile;
    private String submittedWechatId;
    private String sourceType;
    private Long sourceUserId;
    /** 销售自拓时明确关联的新媒体提供方；为空表示未关联。 */
    private Long sourceProviderUserId;
    private Boolean sourceProviderRecorded;
    private Long sourceDeptId;
    private Long partnerId;
    private Long partnerOwnerUserIdSnapshot;
    private String partnerOwnerNameSnapshot;
    private String sourceChannelId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String leadCategory;
    private String leadCategoryLabelSnapshot;
    private String remark;
    private String status;
    private String assignmentStatus;
    private String dispatchMode;
    private Long ownerUserId;
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
