package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper=true)
@TableName("zsjos_teaching_service_event")
public class TeachingServiceEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private String action;
    private String reason;
    private Long operatorUserId;
    private String operatorName;
    private Long fromClassId;
    private Long targetClassId;
    private Long targetOwnerUserId;
    private String targetClassName;
    private String targetOwnerName;
    private String idempotencyKey;
    private String fingerprint;
    private LocalDateTime occurredAt;
}
