package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
@TableName("zsjos_student_service_archive_attachment")
public class StudentServiceArchiveAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId, infraFileId, fileSize;
    private String fieldKey, side, contentType, originalName;
}
