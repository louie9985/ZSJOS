package cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_feedback")
@KeySequence("zsjos_feedback_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long workOrderId;
    private String feedbackType;
    private String feedbackNo;
    private String title;
    private String titleFieldKey;
    private Long formId;
    private String formSnapshotJson;
    private String valueSnapshotJson;
    private String supportDictType;
    private String supportTypeValue;
    private String supportTypeLabelSnapshot;
    private String status;
    private Long submitterUserId;
    private String submitterNameSnapshot;
    private Long assigneeUserId;
    private String assigneeNameSnapshot;
    private String lastReplySummary;
    private LocalDateTime lastActivityAt;
    private Boolean unreadForSubmitter;
    private Boolean unreadForAssignee;
    private Boolean approvalEnabled;
    private String processInstanceId;
    private Integer approvalRoundNo;
    private String rejectReason;
    private String completedResult;
    private String resultAttachmentIdsJson;
    private Integer configVersion;
    @Version
    private Integer version;
}
