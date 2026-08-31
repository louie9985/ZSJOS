package cn.iocoder.yudao.module.zsjos.dal.dataobject.audit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_business_audit_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessAuditLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long operatorUserId;
    private String operatorNameSnapshot;
    private String operatorRoleSnapshot;
    private String categoryCode;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String detailJson;
    private String sourceIp;
    private String sourceType;
    private String traceId;
    private String requestMethod;
    private String requestPath;
    private String resultStatus;
    private Integer resultCode;
    private String resultMessage;
    private LocalDateTime occurredAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
}
