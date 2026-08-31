package cn.iocoder.yudao.module.zsjos.dal.dataobject.task;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_business_task")
@KeySequence("zsjos_business_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskType;
    private String bizType;
    private Long bizId;
    private String status;
    private String assigneeType;
    private Long assigneeId;
    private String titleSnapshot;
    private String summarySnapshot;
    private String actionCode;
    private LocalDateTime dueAt;
    private LocalDateTime remindAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String payload;
    private String idempotencyKey;
    private Integer version;
}
