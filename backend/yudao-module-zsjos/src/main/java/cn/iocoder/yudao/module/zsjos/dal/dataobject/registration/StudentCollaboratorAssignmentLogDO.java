package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_student_collaborator_assignment_log")
@KeySequence("zsjos_student_collaborator_assignment_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentCollaboratorAssignmentLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private String collaboratorType;
    private Long previousUserId;
    private Long assignedUserId;
    private Long operatorUserId;
    private String reason;
    private String idempotencyKey;
}
