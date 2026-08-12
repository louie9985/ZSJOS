package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_work_task")
@KeySequence("zsjos_work_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private Long planId;
    private Long parentTaskId;
    private String title;
    private String description;
    private String deliverableRequirement;
    private Long assigneeUserId;
    private Long assigneeDeptId;
    private Long assignerUserId;
    private LocalDateTime dueAt;
    private LocalDateTime remindAt;
    private Boolean confirmationRequired;
    private Long confirmerUserId;
    private String status;
    private LocalDateTime reportedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime reminderNotifiedAt;
    private LocalDateTime overdueNotifiedAt;
    private Integer version;
}
