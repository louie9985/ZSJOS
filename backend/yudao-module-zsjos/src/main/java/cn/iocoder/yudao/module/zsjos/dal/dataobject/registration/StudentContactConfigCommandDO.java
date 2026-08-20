package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_student_contact_config_command")
@KeySequence("zsjos_student_contact_config_command_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentContactConfigCommandDO extends TenantBaseDO {
    @TableId private Long id;
    private String operation;
    private String idempotencyKey;
    private Long configId;
    private Integer expectedVersion;
    private String requestFingerprint;
    private Long resultConfigId;
}
