package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Tenant-level field visibility for the exam and certificate archive. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_student_archive_extra_config")
public class StudentArchiveExtraConfigDO extends TenantBaseDO {
    private Long id;
    private Integer revision;
    private String visibilityJson;
}
