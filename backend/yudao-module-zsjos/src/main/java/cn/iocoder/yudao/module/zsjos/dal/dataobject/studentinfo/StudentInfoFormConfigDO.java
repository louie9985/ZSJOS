package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_student_info_form_config", autoResultMap = true)
public class StudentInfoFormConfigDO extends TenantBaseDO {
    private Long id;
    private Integer versionNo, revision;
    private String status, fieldsJson;
    private java.time.LocalDateTime publishedAt;
}
