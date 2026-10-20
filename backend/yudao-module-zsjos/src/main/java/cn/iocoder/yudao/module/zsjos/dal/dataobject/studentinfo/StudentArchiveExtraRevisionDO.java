package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true)
@TableName(value="zsjos_student_archive_extra_revision", autoResultMap=true)
public class StudentArchiveExtraRevisionDO extends TenantBaseDO {
    private Long id, serviceRelationId, operatorUserId;
    private Integer revision;
    private String reason, idempotencyKey, requestHash;
    @TableField(typeHandler=cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @ToString.Exclude private String valuesJson;
}
