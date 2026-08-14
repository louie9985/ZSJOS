package cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_impersonation_request_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImpersonationRequestLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long sessionId;
    private Long administratorUserId;
    private Long targetUserId;
    private String httpMethod;
    private String requestPath;
    private LocalDateTime occurredAt;
}
