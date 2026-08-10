package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_assignment_history")
@KeySequence("zsjos_lead_assignment_history_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAssignmentHistoryDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private String actionType;
    private Long fromOwnerUserId;
    private Long toOwnerUserId;
    private Long operatorUserId;
    private String reason;
    private LocalDateTime occurredAt;
    private Long assignmentRuleId;
    private Integer attemptNo;
    private Long candidateUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime responseAt;
}
