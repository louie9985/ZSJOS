package cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data; import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@TableName("zsjos_student_delivery_submission") @KeySequence("zsjos_student_delivery_submission_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class StudentDeliverySubmissionDO extends TenantBaseDO {
 @TableId private Long id; private Long stageId; private Long templateVersionId; private String fieldValuesJson;
 private String dictionarySnapshotJson; private String attachmentSnapshotJson; private Long submittedBy; private LocalDateTime submittedAt;
}
