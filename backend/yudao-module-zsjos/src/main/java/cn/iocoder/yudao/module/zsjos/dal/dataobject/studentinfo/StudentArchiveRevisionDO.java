package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_student_archive_revision", autoResultMap = true)
public class StudentArchiveRevisionDO extends TenantBaseDO {
    private Long id, formId, personId, operatorUserId;
    private Integer revision;
    private String reason;
    private String idempotencyKey, requestHash;
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String answersJson;
    private String imageIdsJson;
}
