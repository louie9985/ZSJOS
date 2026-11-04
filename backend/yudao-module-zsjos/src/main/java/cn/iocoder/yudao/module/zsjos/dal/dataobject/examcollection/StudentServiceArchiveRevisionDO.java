package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_student_service_archive_revision", autoResultMap = true)
public class StudentServiceArchiveRevisionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private Long personId;
    private Integer revision;
    private Long taskId;
    private Long submissionId;
    private Long operatorUserId;
    private String source;
    private String reason;
    private String idempotencyKey;
    private String requestHash;
    private String fieldsSnapshot;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String answersJson;
    private String attachmentsJson;
}
