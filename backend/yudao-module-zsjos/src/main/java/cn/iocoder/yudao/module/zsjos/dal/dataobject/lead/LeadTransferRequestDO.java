package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_transfer_request")
@KeySequence("zsjos_lead_transfer_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadTransferRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Long fromOwnerUserId;
    private Long requestedOwnerUserId;
    private Long ownerDeptIdSnapshot;
    private Long transferReviewerUserId;
    private String reason;
    private String status;
    private String processInstanceId;
    private String idempotencyKey;
    private LocalDateTime submittedAt;
    private LocalDateTime resolvedAt;
    private String resolutionReason;
}
