package cn.iocoder.yudao.module.zsjos.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_order_command")
@KeySequence("zsjos_order_command_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderCommandDO extends TenantBaseDO {
    @TableId private Long id;
    private String idempotencyKey;
    private Long orderId;
    private Long approvalRoundId;
    private String processInstanceId;
    private String commandType;
    private String taskDefinitionKey;
    private String bpmTaskId;
    private Long operatorUserId;
    private String requestFingerprint;
}
