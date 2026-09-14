package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioninginterview;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("zsjos_student_positioning_interview_attachment")
public class PositioningInterviewAttachmentDO extends TenantBaseDO {
 @TableId private Long id;
 private Long interviewId; private Long studentPersonId; private Long fileId;
 private String fileName; private String mimeType; private Long fileSize; private Long uploadedBy; private String directory;
}

