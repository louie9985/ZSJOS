package cn.iocoder.yudao.module.zsjos.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_order_supervisor_confirmation")
@KeySequence("zsjos_order_supervisor_confirmation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderSupervisorConfirmationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long approvalRoundId;
    private String taskDefinitionKey;
    private Long requesterUserId;
    private Long supervisorUserId;
    private String parentTaskId;
    private String supervisorTaskId;
    private String requestReason;
    private String decisionReason;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private Integer version;
}
