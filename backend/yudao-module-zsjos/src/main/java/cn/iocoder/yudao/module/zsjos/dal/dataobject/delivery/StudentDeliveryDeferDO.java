package cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO; import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import lombok.EqualsAndHashCode; import java.time.LocalDateTime;
@TableName("zsjos_student_delivery_defer") @KeySequence("zsjos_student_delivery_defer_seq") @Data @EqualsAndHashCode(callSuper = true)
public class StudentDeliveryDeferDO extends TenantBaseDO { @TableId private Long id; private Long stageId; private Long requestedBy; private Long supervisorUserId; private Integer requestedDays; private LocalDateTime originalDueAt; private String reason; private String bpmProcessInstanceId; private String status; private LocalDateTime decidedAt; private String decisionReason; }
