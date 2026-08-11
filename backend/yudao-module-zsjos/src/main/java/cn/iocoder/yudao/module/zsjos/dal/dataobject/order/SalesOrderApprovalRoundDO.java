package cn.iocoder.yudao.module.zsjos.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_order_approval_round")
@KeySequence("zsjos_order_approval_round_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderApprovalRoundDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Integer roundNo;
    private String status;
    private String orderSnapshot;
    private String processInstanceId;
    private String processDefinitionKey;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private String rejectedBpmTaskId;
    private String submissionIdempotencyKey;
}
