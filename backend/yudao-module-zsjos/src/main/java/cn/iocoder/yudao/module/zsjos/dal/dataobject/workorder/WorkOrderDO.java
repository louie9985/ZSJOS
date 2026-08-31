package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_work_order")
@KeySequence("zsjos_work_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderDO extends TenantBaseDO {
    @TableId private Long id;
    private String businessType;
    private Long businessId;
    private String orderNo;
    private String sceneCode;
    private Long sceneVersionId;
    private String sceneNameSnapshot;
    private String processorType;
    private String rejectionStrategySnapshot;
    private String candidateQualificationMode;
    private String candidateRoleScopesJson;
    private String candidateDeptScopesJson;
    private String assignmentMode;
    private String sourceSubjectType;
    private Long sourceUserId;
    private Long targetUserId;
    private Long targetDeptId;
    private String sourceNameSnapshot;
    private String targetNameSnapshot;
    private String status;
    private String fieldSnapshotJson;
    private String valueJson;
    private String attachmentIdsJson;
    private String remark;
    private Integer currentRound;
    private String completionRemark;
    private String completionAttachmentIdsJson;
    private String idempotencyKey;
    private String commandSubjectType;
    private Long commandUserId;
    private String requestFingerprint;
    private String returnReason;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private LocalDateTime acceptedAt;
    @Version private Integer version;
}
