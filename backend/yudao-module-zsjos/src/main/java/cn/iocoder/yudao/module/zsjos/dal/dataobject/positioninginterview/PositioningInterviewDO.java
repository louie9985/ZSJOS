package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioninginterview;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.*;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("zsjos_student_positioning_interview")
public class PositioningInterviewDO extends TenantBaseDO {
 @TableId private Long id;
 private Long studentPersonId; private Long serviceRelationId; private Long directorUserId;
 private Long templateId; private Long templateVersionId; private String templateSnapshotJson;
 private String statusOptionsSnapshotJson;
 private String status; private Integer version; private LocalDate collectedAt;
 private LocalDateTime completedAt; private Long completedBy;
 private String idempotencyKey; private String requestFingerprint;
}
