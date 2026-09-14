package cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_student_delivery_stage")
@KeySequence("zsjos_student_delivery_stage_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class StudentDeliveryStageDO extends TenantBaseDO {
    @TableId private Long id; private Long planId; private Long accountId; private String stageCode; private Long directorUserId;
    private LocalDateTime triggerAt; private LocalDateTime dueAt; private LocalDateTime completedAt; private Long completedBy;
    private String status; private Integer deferDays; private String deferReason; private String bpmProcessInstanceId;
    private Long formVersionId; private String submissionJson; private Integer version;
}
