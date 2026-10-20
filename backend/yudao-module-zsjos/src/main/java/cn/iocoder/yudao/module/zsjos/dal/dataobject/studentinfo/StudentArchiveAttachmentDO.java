package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true)
@TableName("zsjos_student_archive_attachment")
public class StudentArchiveAttachmentDO extends TenantBaseDO {
    private Long id, serviceRelationId, infraFileId, fileSize;
    private String fieldKey, contentType;
}
