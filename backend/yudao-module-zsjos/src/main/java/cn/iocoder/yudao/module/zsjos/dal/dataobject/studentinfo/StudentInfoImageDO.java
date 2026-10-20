package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
@TableName("zsjos_student_info_image")
public class StudentInfoImageDO extends TenantBaseDO {
    private Long id, formId, infraFileId, fileSize;
    private String fieldKey, side, contentType;
    private Boolean archiveOnly;
}
