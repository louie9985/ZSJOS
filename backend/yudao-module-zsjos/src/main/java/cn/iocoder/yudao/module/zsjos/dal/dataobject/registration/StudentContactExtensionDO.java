package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_student_contact_extension")
@KeySequence("zsjos_student_contact_extension_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentContactExtensionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private Long taskId;
    private String status;
    private LocalDateTime originalDueAt;
    private LocalDateTime requestedDueAt;
    private String reasonValue;
    private String reasonLabelSnapshot;
    private String description;
    private String attachmentFileIdsJson;
    private Long applicantUserId;
    private Long reviewerUserId;
    private String processInstanceId;
    private String decisionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime resolvedAt;
    private String idempotencyKey;
    private Integer version;
}
