package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_registration_command")
@KeySequence("zsjos_registration_command_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationCommandDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationCaseId;
    private String commandType;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long operatorUserId;
}
