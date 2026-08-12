package cn.iocoder.yudao.module.zsjos.dal.dataobject.task;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_business_task_notify_stage")
@KeySequence("zsjos_business_task_notify_stage_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessTaskNotifyStageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Long notifyRuleId;
    private String stage;
    private LocalDateTime emittedAt;
}
