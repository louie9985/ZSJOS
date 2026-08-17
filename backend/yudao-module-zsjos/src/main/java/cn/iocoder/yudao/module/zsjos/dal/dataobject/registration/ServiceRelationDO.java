package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_service_relation")
@KeySequence("zsjos_service_relation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceRelationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long personId;
    private Long orderId;
    private Long orderItemId;
    private Long registrationCaseId;
    private String status;
    private Long ownerUserId;
    private String serviceSnapshot;
    private LocalDateTime activatedAt;
    private LocalDateTime pausedAt;
    private String pauseReason;
    private LocalDateTime completedAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;
    private Integer version;
}
