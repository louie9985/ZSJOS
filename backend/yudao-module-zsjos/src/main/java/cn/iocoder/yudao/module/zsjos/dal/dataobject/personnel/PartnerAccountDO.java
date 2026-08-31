package cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_partner_account")
@KeySequence("zsjos_partner_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerAccountDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long partnerId;
    private String mobile;
    private String password;
    private Integer status;
    private Boolean wecomEnabled;
    private String lastLoginIp;
    private LocalDateTime lastLoginTime;
    @Version
    private Integer version;
}
