package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_follow_up_record")
@KeySequence("zsjos_lead_follow_up_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadFollowUpRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Long assignmentHistoryId;
    private Long operatorUserId;
    private Long ownerUserIdSnapshot;
    private Long ownerDeptIdSnapshot;
    private String methodValue;
    private String methodLabelSnapshot;
    private String resultValue;
    private String resultLabelSnapshot;
    private String categoryBefore;
    private String categoryBeforeLabelSnapshot;
    private String categoryAfter;
    private String categoryAfterLabelSnapshot;
    private String remark;
    private LocalDateTime nextFollowUpAt;
    private LocalDateTime occurredAt;
    private Boolean firstInAssignment;
    private String idempotencyKey;
}
