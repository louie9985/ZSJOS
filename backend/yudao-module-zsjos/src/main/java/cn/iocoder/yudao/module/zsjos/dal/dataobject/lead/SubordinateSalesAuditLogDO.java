package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_subordinate_sales_audit_log")
@KeySequence("zsjos_subordinate_sales_audit_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubordinateSalesAuditLogDO extends TenantBaseDO {
    @TableId private Long id;
    private String actionType;
    private Long operatorUserId;
    private Long targetUserId;
    private Long leadId;
    private String beforeValue;
    private String afterValue;
    private String reason;
    private LocalDateTime occurredAt;
}
