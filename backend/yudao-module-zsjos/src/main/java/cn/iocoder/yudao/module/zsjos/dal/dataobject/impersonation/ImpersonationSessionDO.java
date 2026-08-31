package cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_impersonation_session")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImpersonationSessionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long administratorUserId;
    private String administratorNameSnapshot;
    private Long targetUserId;
    private String targetNameSnapshot;
    private String reason;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime endedAt;
    private String endedReason;
    private Integer version;
}
