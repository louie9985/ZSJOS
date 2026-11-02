package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_student_info_task")
public class StudentInfoTaskDO extends TenantBaseDO {
    private Long id, orderId, orderItemId, personId, formId, sourceFormId;
    private Integer version, sourceRevision;
    private String lastCommandKey, lastCommandFingerprint;
    private Long operatorUserId;
}
