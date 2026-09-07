package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_student_info_form", autoResultMap = true)
public class StudentInfoFormDO extends TenantBaseDO {
    private Long id, leadId, salesUserId, configVersionId;
    private String tokenHash, status, submitSource;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String tokenCiphertext;
    private java.time.LocalDateTime expiresAt, revokedAt, submittedAt;
}
